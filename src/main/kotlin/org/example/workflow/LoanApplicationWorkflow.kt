package org.example.workflow

import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.VirtualObject
import dev.restate.sdk.kotlin.ObjectContext
import org.example.model.*
import org.example.service.ContractGenerationServiceClient
import org.example.service.CreditCheckServiceClient
import org.example.service.DecisionServiceClient
import org.slf4j.LoggerFactory

@VirtualObject
class LoanApplicationWorkflow {

    private val logger = LoggerFactory.getLogger(LoanApplicationWorkflow::class.java)

    @Handler
    suspend fun processApplication(ctx: ObjectContext, application: LoanApplication): LoanResult {
        logger.info("Starting loan application workflow for: {}", application.applicationId)

        // Step 1: Credit check (with automatic retries via Restate)
        logger.info("Running credit check for application: {}", application.applicationId)

        // Call credit check service with retry policy
        val creditCheckClient = CreditCheckServiceClient.fromContext(ctx)
        val creditResult = creditCheckClient.checkCredit(application).await()

        val creditScore = creditResult.score
        logger.info(
            "Credit check completed for application: {}, score: {}",
            application.applicationId,
            creditScore
        )

        // Step 2: Make decision based on credit score
        logger.info("Making decision for application: {}", application.applicationId)

        val decisionClient = DecisionServiceClient.fromContext(ctx)
        val decision = decisionClient.makeDecision(DecisionRequest(application, creditScore)).await()

        logger.info("Decision for application {}: {}", application.applicationId, decision)

        return when (decision) {
            Decision.APPROVED -> {
                // Generate contract for approved applications
                logger.info("Generating contract for approved application: {}", application.applicationId)

                val contractClient = ContractGenerationServiceClient.fromContext(ctx)
                val contractResult = contractClient.generateContract(application).await()

                LoanResult(
                    applicationId = application.applicationId,
                    decision = Decision.APPROVED,
                    creditScore = creditScore,
                    message = "Congratulations! Your loan application has been approved. Contract: ${contractResult.contractId}",
                    contractId = contractResult.contractId
                )
            }

            Decision.REJECTED -> {
                logger.info("Application rejected: {}", application.applicationId)
                LoanResult(
                    applicationId = application.applicationId,
                    decision = Decision.REJECTED,
                    creditScore = creditScore,
                    message = "We're sorry, your loan application has been rejected based on credit assessment."
                )
            }

            Decision.MANUAL_REVIEW -> {
                // For POC: Return manual review status
                // In a full implementation, this would use durable promises and await signals
                logger.info("Application requires manual review: {}", application.applicationId)
                LoanResult(
                    applicationId = application.applicationId,
                    decision = Decision.MANUAL_REVIEW,
                    creditScore = creditScore,
                    message = "Application pending manual review. Please contact our team."
                )
            }
        }
    }
}
