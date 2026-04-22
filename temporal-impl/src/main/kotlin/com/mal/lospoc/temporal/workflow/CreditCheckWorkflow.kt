package com.mal.lospoc.temporal.workflow

import com.mal.lospoc.common.client.WorkflowClient
import com.mal.lospoc.common.domain.ApplicationEvent
import com.mal.lospoc.common.domain.LoanProductConfig
import com.mal.lospoc.common.dto.*
import io.temporal.activity.ActivityInterface
import io.temporal.activity.ActivityOptions
import io.temporal.common.RetryOptions
import io.temporal.workflow.Workflow
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID

data class CreditCheckRequest(
    val productId: String,
    val userDetails: UserDetails,
    val loanAmount: BigDecimal,
    val productConfig: LoanProductConfig,
    val httpbinUrl: String,
    val losUrl: String
)

data class WorkflowResult(
    val applicationId: UUID,
    val status: String,
    val approvedAmount: BigDecimal = BigDecimal.ZERO,
    val decisionReason: String = ""
)

@WorkflowInterface
interface CreditCheckWorkflow {
    @WorkflowMethod
    fun run(req: CreditCheckRequest): WorkflowResult
}

class CreditCheckWorkflowImpl : CreditCheckWorkflow {

    private val activities = Workflow.newActivityStub(
        Activities::class.java,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(60))
            .setScheduleToCloseTimeout(Duration.ofSeconds(90))
            .setScheduleToStartTimeout(Duration.ofSeconds(30))
            .setRetryOptions(
                RetryOptions.newBuilder()
                    .setInitialInterval(Duration.ofMillis(100))
                    .setMaximumInterval(Duration.ofSeconds(1))
                    .setBackoffCoefficient(2.0)
                    .setMaximumAttempts(1)  // Disable retries for benchmark parity with Restate
                    .setDoNotRetry("java.lang.IllegalArgumentException")
                    .build()
            )
            .build()
    )

    override fun run(req: CreditCheckRequest): WorkflowResult {
        val config = req.productConfig

        try {
            // Submit application to LOS and get generated ID
            val appId = activities.submitApplication(req.productId, req.userDetails, req.loanAmount, req.losUrl)

            // Stage 1: Identity Verification
            val identity = activities.identityVerification(appId, req.userDetails.emiratesId, req.httpbinUrl, req.losUrl)

            // Stage 2: Credit Bureau
            val creditBureau = activities.creditBureau(appId, req.userDetails.emiratesId, identity.verificationId, req.httpbinUrl, req.losUrl)

            // Stage 3: Open Banking (conditional)
            val openBanking = if (config.openBanking.enabled) {
                activities.openBanking(appId, req.httpbinUrl, req.losUrl)
            } else null

            // Stage 4: Employment Verification (conditional)
            if (config.employmentVerification.enabled) {
                activities.employmentVerification(appId, req.userDetails.emiratesId, req.httpbinUrl, req.losUrl)
            }

            // Stage 5: AML Screening (conditional)
            if (config.amlScreening.enabled) {
                activities.amlScreening(appId, req.userDetails.emiratesId, req.httpbinUrl, req.losUrl)
            }

            // Stage 6: Fraud Scoring (conditional)
            val fraud = if (config.fraudScoring.enabled) {
                activities.fraudScoring(appId, creditBureau, req.httpbinUrl, req.losUrl)
            } else FraudScore("n/a", 100, "LOW", 0)

            // Decision
            val decision = activities.decision(appId, creditBureau, openBanking, fraud, req.productId, req.httpbinUrl, req.losUrl)

            // Stage 7: Disbursement Notification (conditional, only if approved)
            if (decision.outcome == RiskScore.Outcome.AUTO_APPROVE && config.disbursementNotification.enabled) {
                activities.disbursementNotification(appId, req.loanAmount, req.httpbinUrl, req.losUrl)
            }

            // Return result
            return when (decision.outcome) {
                RiskScore.Outcome.AUTO_APPROVE -> WorkflowResult(
                    applicationId = appId,
                    status = "approved",
                    approvedAmount = req.loanAmount,
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
            return WorkflowResult(
                applicationId = UUID.randomUUID(), // Fallback ID
                status = "failed",
                decisionReason = e.message ?: "Unknown error"
            )
        }
    }

    @ActivityInterface
    interface Activities {
        fun submitApplication(productId: String, userDetails: UserDetails, loanAmount: BigDecimal, losUrl: String): UUID
        fun identityVerification(appId: UUID, emiratesId: String, httpbinUrl: String, losUrl: String): IdentityVerificationResult
        fun creditBureau(appId: UUID, emiratesId: String, verificationId: String, httpbinUrl: String, losUrl: String): CreditBureauReport
        fun openBanking(appId: UUID, httpbinUrl: String, losUrl: String): OpenBankingSnapshot
        fun employmentVerification(appId: UUID, emiratesId: String, httpbinUrl: String, losUrl: String): EmploymentRecord
        fun amlScreening(appId: UUID, emiratesId: String, httpbinUrl: String, losUrl: String): AmlScreeningResult
        fun fraudScoring(appId: UUID, creditReport: CreditBureauReport, httpbinUrl: String, losUrl: String): FraudScore
        fun decision(appId: UUID, credit: CreditBureauReport, ob: OpenBankingSnapshot?, fraud: FraudScore, productId: String, httpbinUrl: String, losUrl: String): RiskScore
        fun disbursementNotification(appId: UUID, amount: BigDecimal, httpbinUrl: String, losUrl: String): DisbursementConfirmation
    }

    class ActivitiesImpl(
        private val httpbinUrl: String,
        private val losUrl: String
    ) : Activities {
        // Singleton WorkflowClient shared across all activity invocations
        private val client = WorkflowClient(httpbinUrl, losUrl)

        override fun submitApplication(productId: String, userDetails: UserDetails, loanAmount: BigDecimal, losUrl: String): UUID {
            return client.submitApplication(productId, userDetails, loanAmount)
        }

        override fun identityVerification(appId: UUID, emiratesId: String, httpbinUrl: String, losUrl: String): IdentityVerificationResult {
            val result = client.verifyIdentity(emiratesId)
            client.notifyLos(appId, ApplicationEvent.StageCompleted("identity_verification", result))
            return result
        }

        override fun creditBureau(appId: UUID, emiratesId: String, verificationId: String, httpbinUrl: String, losUrl: String): CreditBureauReport {
            val result = client.fetchCreditBureau(emiratesId, verificationId)
            client.notifyLos(appId, ApplicationEvent.StageCompleted("credit_bureau", result))
            return result
        }

        override fun openBanking(appId: UUID, httpbinUrl: String, losUrl: String): OpenBankingSnapshot {
            val result = client.fetchOpenBanking(appId)
            client.notifyLos(appId, ApplicationEvent.StageCompleted("open_banking", result))
            return result
        }

        override fun employmentVerification(appId: UUID, emiratesId: String, httpbinUrl: String, losUrl: String): EmploymentRecord {
            val result = client.verifyEmployment(emiratesId)
            client.notifyLos(appId, ApplicationEvent.StageCompleted("employment_verification", result))
            return result
        }

        override fun amlScreening(appId: UUID, emiratesId: String, httpbinUrl: String, losUrl: String): AmlScreeningResult {
            val result = client.screenAml(appId, emiratesId)
            client.notifyLos(appId, ApplicationEvent.StageCompleted("aml_screening", result))
            return result
        }

        override fun fraudScoring(appId: UUID, creditReport: CreditBureauReport, httpbinUrl: String, losUrl: String): FraudScore {
            val result = client.scoreFraud(appId, creditReport)
            client.notifyLos(appId, ApplicationEvent.StageCompleted("fraud_scoring", result))
            return result
        }

        override fun decision(appId: UUID, credit: CreditBureauReport, ob: OpenBankingSnapshot?, fraud: FraudScore, productId: String, httpbinUrl: String, losUrl: String): RiskScore {
            val result = client.scoreApplication(credit, ob, fraud, productId)
            client.notifyLos(appId, ApplicationEvent.DecisionMade(result))
            return result
        }

        override fun disbursementNotification(appId: UUID, amount: BigDecimal, httpbinUrl: String, losUrl: String): DisbursementConfirmation {
            val result = client.notifyDisbursement(appId, amount, "DEFAULT_ACCOUNT")
            client.notifyLos(appId, ApplicationEvent.StageCompleted("disbursement_notification", result))
            return result
        }
    }
}