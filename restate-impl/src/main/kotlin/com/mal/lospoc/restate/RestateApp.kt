package com.mal.lospoc.restate

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.mal.lospoc.common.client.WorkflowClient
import com.mal.lospoc.common.domain.ApplicationEvent
import com.mal.lospoc.common.domain.LoanProductConfig
import com.mal.lospoc.common.dto.*
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val LOS_URL = "http://localhost:8000"
private const val HTTPBIN_URL = "http://localhost:8091"

private val json = ObjectMapper()
    .registerModule(JavaTimeModule())
    .registerKotlinModule()

data class SubmitRequest(
    val productId: String,
    val userDetails: UserDetails,
    val loanAmount: BigDecimal
)

fun main() {
    // HTTP server for application submissions
    val httpClient = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    val server = HttpServer.create(InetSocketAddress(9000), 0)

    server.createContext("/") { exchange ->
        if (exchange.requestMethod == "GET") {
            sendResponse(exchange, 200, mapOf("status" to "ok", "service" to "restate"))
        } else {
            sendResponse(exchange, 405, mapOf("error" to "Method not allowed"))
        }
    }

    server.createContext("/api/applications") { exchange ->
        if (exchange.requestMethod != "POST") {
            sendResponse(exchange, 405, mapOf("error" to "Method not allowed"))
            return@createContext
        }

        try {
            val body = exchange.requestBody.readAllBytes().decodeToString()
            val req = json.readValue(body, SubmitRequest::class.java)

            val config = createDefaultConfig(req.productId)

            // Execute workflow synchronously and wait for completion
            val result = executeWorkflow(req.productId, req.userDetails, req.loanAmount, config)

            // Map result to HTTP response
            when (result.status) {
                "approved" -> sendResponse(exchange, 200, mapOf(
                    "applicationId" to result.applicationId.toString(),
                    "status" to "approved",
                    "approvedAmount" to result.approvedAmount.toString(),
                    "decisionReason" to result.decisionReason
                ))
                "rejected" -> sendResponse(exchange, 200, mapOf(
                    "applicationId" to result.applicationId.toString(),
                    "status" to "rejected",
                    "decisionReason" to result.decisionReason
                ))
                "timeout" -> sendResponse(exchange, 504, mapOf(
                    "applicationId" to result.applicationId.toString(),
                    "error" to "Gateway Timeout",
                    "message" to result.decisionReason
                ))
                else -> sendResponse(exchange, 500, mapOf(
                    "applicationId" to result.applicationId.toString(),
                    "error" to "Internal Server Error",
                    "message" to result.decisionReason
                ))
            }
        } catch (e: Exception) {
            sendResponse(exchange, 500, mapOf("error" to (e.message ?: "Unknown error")))
        }
    }

    server.executor = Executors.newFixedThreadPool(100)
    server.start()
    println("Restate HTTP server started on port 9000")
}

data class WorkflowResult(
    val applicationId: UUID,
    val status: String,
    val approvedAmount: BigDecimal = BigDecimal.ZERO,
    val decisionReason: String = ""
)

// Execute workflow synchronously - blocks until workflow completes
private fun executeWorkflow(
    productId: String,
    userDetails: UserDetails,
    loanAmount: BigDecimal,
    config: LoanProductConfig
): WorkflowResult {
    val client = WorkflowClient(HTTPBIN_URL, LOS_URL)

    return try {
        // Submit application to LOS and get generated ID
        val appId = client.submitApplication(productId, userDetails, loanAmount)

        // Stage 1: Identity Verification
        val identity = client.verifyIdentity(userDetails.emiratesId)
        client.notifyLos(appId, ApplicationEvent.StageCompleted("identity_verification", identity))

        // Stage 2: Credit Bureau
        val creditBureau = client.fetchCreditBureau(userDetails.emiratesId, identity.verificationId)
        client.notifyLos(appId, ApplicationEvent.StageCompleted("credit_bureau", creditBureau))

        // Stage 3: Open Banking (conditional)
        val openBanking = if (config.openBanking.enabled) {
            val result = client.fetchOpenBanking(appId)
            client.notifyLos(appId, ApplicationEvent.StageCompleted("open_banking", result))
            result
        } else null

        // Stage 4: Employment Verification (conditional)
        if (config.employmentVerification.enabled) {
            val employment = client.verifyEmployment(userDetails.emiratesId)
            client.notifyLos(appId, ApplicationEvent.StageCompleted("employment_verification", employment))
        }

        // Stage 5: AML Screening (conditional)
        if (config.amlScreening.enabled) {
            val aml = client.screenAml(appId, userDetails.emiratesId)
            client.notifyLos(appId, ApplicationEvent.StageCompleted("aml_screening", aml))
        }

        // Stage 6: Fraud Scoring (conditional)
        val fraud = if (config.fraudScoring.enabled) {
            val result = client.scoreFraud(appId, creditBureau)
            client.notifyLos(appId, ApplicationEvent.StageCompleted("fraud_scoring", result))
            result
        } else FraudScore("n/a", 100, "LOW", 0)

        // Decision
        val decision = client.scoreApplication(creditBureau, openBanking, fraud, productId)
        client.notifyLos(appId, ApplicationEvent.DecisionMade(decision))

        // Stage 7: Disbursement Notification (conditional, only if approved)
        if (decision.outcome == RiskScore.Outcome.AUTO_APPROVE && config.disbursementNotification.enabled) {
            val disburse = client.notifyDisbursement(appId, loanAmount, "DEFAULT_ACCOUNT")
            client.notifyLos(appId, ApplicationEvent.StageCompleted("disbursement_notification", disburse))
        }

        // Return result based on decision
        when (decision.outcome) {
            RiskScore.Outcome.AUTO_APPROVE -> WorkflowResult(
                applicationId = appId,
                status = "approved",
                approvedAmount = loanAmount,
                decisionReason = "Auto-approved with score ${decision.score}"
            )
            RiskScore.Outcome.AUTO_REJECT -> WorkflowResult(
                applicationId = appId,
                status = "rejected",
                decisionReason = "Auto-rejected with score ${decision.score}"
            )
            RiskScore.Outcome.MANUAL -> WorkflowResult(
                applicationId = appId,
                status = "rejected",
                decisionReason = "Manual review required (score ${decision.score})"
            )
        }
    } catch (e: Exception) {
        WorkflowResult(
            applicationId = UUID.randomUUID(), // Fallback ID in case of early failure
            status = "failed",
            decisionReason = e.message ?: "Unknown error"
        )
    }
}

private fun sendResponse(exchange: HttpExchange, code: Int, body: Any) {
    val response = json.writeValueAsString(body)
    exchange.responseHeaders["Content-Type"] = "application/json"
    exchange.responseHeaders["Connection"] = "keep-alive"
    exchange.sendResponseHeaders(code, response.length.toLong())
    exchange.responseBody.use { os ->
        os.write(response.toByteArray())
    }
}

private fun createDefaultConfig(productId: String): LoanProductConfig {
    val enabled = LoanProductConfig.StageConfig(true, 30, 3)
    val disabled = LoanProductConfig.StageConfig(false, 0, 0)
    val thresholds = LoanProductConfig.DecisionThresholds(700, 500)

    return when (productId) {
        "auto_loan" -> LoanProductConfig(
            productId,
            enabled,  // identityVerification
            enabled,  // creditBureau
            enabled,  // openBanking
            disabled, // employmentVerification
            enabled,  // amlScreening
            disabled, // fraudScoring
            enabled,  // disbursementNotification
            thresholds,
            Duration.ofDays(5)
        )
        else -> LoanProductConfig(
            productId,
            enabled,  // identityVerification
            enabled,  // creditBureau
            enabled,  // openBanking
            enabled,  // employmentVerification
            enabled,  // amlScreening
            enabled,  // fraudScoring
            enabled,  // disbursementNotification
            thresholds,
            Duration.ofDays(7)
        )
    }
}
