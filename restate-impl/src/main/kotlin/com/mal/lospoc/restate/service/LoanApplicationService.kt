package com.mal.lospoc.restate.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.mal.lospoc.common.domain.LoanProductConfig
import com.mal.lospoc.restate.controller.SubmitRequest
import com.mal.lospoc.restate.workflow.LoanApplicationRequest
import com.mal.lospoc.restate.workflow.LoanApplicationResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service
class LoanApplicationService(
    @Value("\${restate.ingress-url}") private val restateIngressUrl: String,
    @Value("\${restate.los-url}") private val losUrl: String,
    @Value("\${restate.httpbin-url}") private val httpbinUrl: String
) {
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = ObjectMapper()
        .registerModule(JavaTimeModule())
        .registerKotlinModule()

    fun submitApplication(request: SubmitRequest): LoanApplicationResult {
        val config = createDefaultConfig(request.productId)
        val serviceRequest = LoanApplicationRequest(
            productId = request.productId,
            userDetails = request.userDetails,
            loanAmount = request.loanAmount,
            config = config,
            httpbinUrl = httpbinUrl,
            losUrl = losUrl
        )

        return try {
            invokeRestateWorkflow(serviceRequest)
        } catch (e: Exception) {
            LoanApplicationResult(
                applicationId = UUID.randomUUID(),
                status = "failed",
                decisionReason = e.message ?: "Service execution failed"
            )
        }
    }

    private fun invokeRestateWorkflow(request: LoanApplicationRequest): LoanApplicationResult {
        val requestBody = json.writeValueAsString(request)
        val httpRequest = Request.Builder()
            .url("$restateIngressUrl/LoanApplicationService/process")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Restate invocation failed: ${response.code} ${response.message}")
            }
            return json.readValue(response.body?.string(), LoanApplicationResult::class.java)
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
}
