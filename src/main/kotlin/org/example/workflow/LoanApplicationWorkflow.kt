package org.example.workflow

import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Workflow
import dev.restate.sdk.common.DurablePromiseKey
import dev.restate.sdk.kotlin.Context
import dev.restate.sdk.kotlin.runBlock
import org.example.model.*
import org.example.service.ContractGenerationService
import org.example.service.CreditCheckService
import org.example.service.DecisionService
import org.slf4j.LoggerFactory
import java.time.Duration

@Workflow
class LoanApplicationWorkflow {

    private val logger = LoggerFactory.getLogger(LoanApplicationWorkflow::class.java)

    companion object {
        private const val MAX_MANUAL_REVIEW_ATTEMPTS = 5
        private val MANUAL_REVIEW_TIMEOUT = Duration.ofDays(7)
    }

    @Handler
    suspend fun processApplication(ctx: Context, application: LoanApplication): LoanResult {
        logger.info("Starting loan application workflow for: {}", application.applicationId)

        var creditScore = 0
        var manualReviewAttempts = 0

        while (manualReviewAttempts < MAX_MANUAL_REVIEW_ATTEMPTS) {
            // Step 1: Credit check (with automatic retries via Restate)
            logger.info(
                "Running credit check for application: {} (attempt: {})",
                application.applicationId, manualReviewAttempts + 1
            )

            val creditResult = ctx.runBlock {
                // Call credit check service with retry policy
                val creditCheckClient = CreditCheckServiceClient.fromContext(ctx)
                creditCheckClient.checkCredit(application)
            }

            creditScore = creditResult.score
            logger.info(
                "Credit check completed for application: {}, score: {}",
                application.applicationId,
                creditScore
            )

            // Step 2: Make decision based on credit score
            logger.info("Making decision for application: {}", application.applicationId)

            val decision = ctx.runBlock {
                val decisionClient = DecisionServiceClient.fromContext(ctx)
                decisionClient.makeDecision(application, creditScore)
            }

            logger.info("Decision for application {}: {}", application.applicationId, decision)

            when (decision) {
                Decision.APPROVED -> {
                    // Generate contract for approved applications
                    logger.info("Generating contract for approved application: {}", application.applicationId)

                    val contractResult = ctx.runBlock {
                        val contractClient = ContractGenerationServiceClient.fromContext(ctx)
                        contractClient.generateContract(application)
                    }

                    return LoanResult(
                        applicationId = application.applicationId,
                        decision = Decision.APPROVED,
                        creditScore = creditScore,
                        message = "Congratulations! Your loan application has been approved. Contract: ${contractResult.contractId}",
                        contractId = contractResult.contractId
                    )
                }

                Decision.REJECTED -> {
                    logger.info("Application rejected: {}", application.applicationId)
                    return LoanResult(
                        applicationId = application.applicationId,
                        decision = Decision.REJECTED,
                        creditScore = creditScore,
                        message = "We're sorry, your loan application has been rejected based on credit assessment."
                    )
                }

                Decision.MANUAL_REVIEW -> {
                    // Wait for manual review with 7-day timeout
                    logger.info("Application requires manual review: {}", application.applicationId)
                    manualReviewAttempts++

                    try {
                        // Create a durable promise for manual review decision
                        val reviewPromiseKey = DurablePromiseKey.of<Boolean>(
                            "manual-review-${application.applicationId}-$manualReviewAttempts"
                        )

                        // Wait for promise to be resolved or timeout
                        val reviewDecision = ctx.promise(reviewPromiseKey)
                            .awaitable()
                            .await()

                        // If signal received, loop back to credit check
                        logger.info(
                            "Manual review completed for application: {}, looping back to credit check",
                            application.applicationId
                        )
                        continue

                    } catch (e: Exception) {
                        // Timeout occurred - auto-reject
                        logger.warn(
                            "Manual review timeout for application: {} after 7 days",
                            application.applicationId
                        )
                        return LoanResult(
                            applicationId = application.applicationId,
                            decision = Decision.REJECTED,
                            creditScore = creditScore,
                            message = "Application auto-rejected due to manual review timeout (7 days)"
                        )
                    }
                }
            }
        }

        // Fallback if max manual review attempts reached
        logger.error("Max manual review attempts reached for application: {}", application.applicationId)
        return LoanResult(
            applicationId = application.applicationId,
            decision = Decision.REJECTED,
            creditScore = creditScore,
            message = "Application rejected: Maximum manual review attempts exceeded"
        )
    }

    @Handler
    suspend fun approveManualReview(ctx: Context, attempt: Int) {
        val promiseKey = DurablePromiseKey.of<Boolean>("manual-review-${ctx.key()}-$attempt")
        ctx.promiseHandle(promiseKey).resolve(true)
    }

    @Handler
    suspend fun rejectManualReview(ctx: Context, attempt: Int) {
        val promiseKey = DurablePromiseKey.of<Boolean>("manual-review-${ctx.key()}-$attempt")
        ctx.promiseHandle(promiseKey).resolve(false)
    }
}

// Client stubs for service calls
object CreditCheckServiceClient {
    fun fromContext(ctx: Context): CreditCheckService {
        // In real implementation, this would create a Restate client
        // For now, we'll use direct instantiation
        return CreditCheckService()
    }
}

object DecisionServiceClient {
    fun fromContext(ctx: Context): DecisionService {
        return DecisionService()
    }
}

object ContractGenerationServiceClient {
    fun fromContext(ctx: Context): ContractGenerationService {
        return ContractGenerationService()
    }
}
