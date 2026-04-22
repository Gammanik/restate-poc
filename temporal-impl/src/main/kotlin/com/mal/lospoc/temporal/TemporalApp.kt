package com.mal.lospoc.temporal

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.mal.lospoc.common.domain.LoanProductConfig
import com.mal.lospoc.common.dto.UserDetails
import com.mal.lospoc.temporal.workflow.CreditCheckRequest
import com.mal.lospoc.temporal.workflow.CreditCheckWorkflow
import com.mal.lospoc.temporal.workflow.CreditCheckWorkflowImpl
import com.mal.lospoc.temporal.workflow.WorkflowResult
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.worker.WorkerFactory
import io.temporal.worker.WorkerOptions
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

const val TASK_QUEUE = "CREDIT_CHECK_QUEUE"
private val json = ObjectMapper()
    .registerModule(JavaTimeModule())
    .registerKotlinModule()
private const val LOS_URL = "http://localhost:8000"
private const val HTTPBIN_URL = "http://localhost:8091"

data class SubmitRequest(
    val productId: String,
    val userDetails: UserDetails,
    val loanAmount: BigDecimal
)

fun main() {
    val service = WorkflowServiceStubs.newLocalServiceStubs()
    val client = WorkflowClient.newInstance(service)
    val factory = WorkerFactory.newInstance(client)

    // Worker tuning for high throughput
    val workerOptions = WorkerOptions.newBuilder()
        .setMaxConcurrentWorkflowTaskExecutionSize(500)
        .setMaxConcurrentActivityExecutionSize(500)
        .build()

    val worker = factory.newWorker(TASK_QUEUE, workerOptions)
    worker.registerWorkflowImplementationTypes(CreditCheckWorkflowImpl::class.java)
    worker.registerActivitiesImplementations(CreditCheckWorkflowImpl.ActivitiesImpl(HTTPBIN_URL, LOS_URL))

    factory.start()

    // Increase backlog queue size for high RPS (default 0 = system default ~50)
    val server = HttpServer.create(InetSocketAddress(9002), 1000)

    server.createContext("/") { exchange ->
        if (exchange.requestMethod == "GET") {
            sendResponse(exchange, 200, mapOf("status" to "ok", "service" to "temporal"))
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

            // Generate unique workflow ID with timestamp to avoid conflicts at high RPS
            val workflowId = "credit-check-${UUID.randomUUID()}-${System.nanoTime()}"

            val workflow = client.newWorkflowStub(
                CreditCheckWorkflow::class.java,
                WorkflowOptions.newBuilder()
                    .setTaskQueue(TASK_QUEUE)
                    .setWorkflowId(workflowId)
                    .setWorkflowExecutionTimeout(Duration.ofSeconds(120))
                    .setWorkflowTaskTimeout(Duration.ofSeconds(10))
                    .build()
            )

            val workflowReq = CreditCheckRequest(
                req.productId, req.userDetails, req.loanAmount, config, HTTPBIN_URL, LOS_URL
            )

            // Synchronous execution - blocks until workflow completes
            val result = try {
                workflow.run(workflowReq)
            } catch (e: Exception) {
                if (e.message?.contains("timeout", ignoreCase = true) == true) {
                    WorkflowResult(applicationId = UUID.randomUUID(), status = "timeout", decisionReason = "Workflow timeout after 30 seconds")
                } else {
                    WorkflowResult(applicationId = UUID.randomUUID(), status = "failed", decisionReason = e.message ?: "Workflow execution failed")
                }
            }

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

    // Use larger thread pool to handle high concurrency at 50+ RPS
    // At 50 RPS with ~200ms avg latency, need ~10 threads minimum
    // Using 500 to match worker concurrency and handle spikes
    server.executor = Executors.newFixedThreadPool(500)
    server.start()

    println("Temporal worker and HTTP server started on port 9002")
    println("Worker config: maxConcurrentWorkflowTasks=500, maxConcurrentActivityTasks=500")
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
