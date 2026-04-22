package com.mal.lospoc.temporal.service

import com.mal.lospoc.common.domain.LoanProductConfig
import com.mal.lospoc.temporal.controller.SubmitRequest
import com.mal.lospoc.temporal.workflow.CreditCheckRequest
import com.mal.lospoc.temporal.workflow.CreditCheckWorkflow
import com.mal.lospoc.temporal.workflow.WorkflowResult
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

@Service
class LoanApplicationService(
    private val workflowClient: WorkflowClient,
    @Value("\${temporal.task-queue}") private val taskQueue: String,
    @Value("\${temporal.los-url}") private val losUrl: String,
    @Value("\${temporal.httpbin-url}") private val httpbinUrl: String
) {

    fun submitApplication(request: SubmitRequest): WorkflowResult {
        val config = createDefaultConfig(request.productId)

        // Generate unique workflow ID with timestamp to avoid conflicts at high RPS
        val workflowId = "credit-check-${UUID.randomUUID()}-${System.nanoTime()}"

        val workflow = workflowClient.newWorkflowStub(
            CreditCheckWorkflow::class.java,
            WorkflowOptions.newBuilder()
                .setTaskQueue(taskQueue)
                .setWorkflowId(workflowId)
                .setWorkflowExecutionTimeout(Duration.ofSeconds(30))
                .build()
        )

        val workflowRequest = CreditCheckRequest(
            request.productId,
            request.userDetails,
            request.loanAmount,
            config,
            httpbinUrl,
            losUrl
        )

        // Synchronous execution - blocks until workflow completes
        return try {
            workflow.run(workflowRequest)
        } catch (e: Exception) {
            if (e.message?.contains("timeout", ignoreCase = true) == true) {
                WorkflowResult(
                    applicationId = UUID.randomUUID(),
                    status = "timeout",
                    decisionReason = "Workflow timeout after 30 seconds"
                )
            } else {
                WorkflowResult(
                    applicationId = UUID.randomUUID(),
                    status = "failed",
                    decisionReason = e.message ?: "Workflow execution failed"
                )
            }
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
