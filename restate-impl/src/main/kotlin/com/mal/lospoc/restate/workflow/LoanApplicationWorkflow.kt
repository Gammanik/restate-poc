package com.mal.lospoc.restate.workflow

import com.mal.lospoc.common.client.WorkflowClient
import com.mal.lospoc.common.domain.ApplicationEvent
import com.mal.lospoc.common.domain.LoanProductConfig
import com.mal.lospoc.common.dto.IdentityVerificationResult
import com.mal.lospoc.common.dto.CreditBureauReport
import com.mal.lospoc.common.dto.OpenBankingSnapshot
import com.mal.lospoc.common.dto.FraudScore
import com.mal.lospoc.common.dto.RiskScore
import com.mal.lospoc.common.dto.UserDetails
import dev.restate.sdk.Context
import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Service
import dev.restate.sdk.serde.jackson.JacksonSerdes
import java.math.BigDecimal
import java.util.UUID

data class LoanApplicationRequest(
    val productId: String,
    val userDetails: UserDetails,
    val loanAmount: BigDecimal,
    val config: LoanProductConfig,
    val httpbinUrl: String,
    val losUrl: String
)

data class LoanApplicationResult(
    val applicationId: UUID,
    val status: String,
    val approvedAmount: BigDecimal? = BigDecimal.ZERO,
    val decisionReason: String = ""
)

@Service
class LoanApplicationService {

    companion object {
        private val IDENTITY_SERDE = JacksonSerdes.of(IdentityVerificationResult::class.java)
        private val CREDIT_BUREAU_SERDE = JacksonSerdes.of(CreditBureauReport::class.java)
        private val OPEN_BANKING_SERDE = JacksonSerdes.of(OpenBankingSnapshot::class.java)
        private val FRAUD_SCORE_SERDE = JacksonSerdes.of(FraudScore::class.java)
        private val RISK_SCORE_SERDE = JacksonSerdes.of(RiskScore::class.java)
        private val UUID_SERDE = JacksonSerdes.of(UUID::class.java)
    }

    @Handler
    fun process(ctx: Context, request: LoanApplicationRequest): LoanApplicationResult {
        val client = WorkflowClient(request.httpbinUrl, request.losUrl)

        return try {
            // Stage 0: Submit application to LOS and get generated ID
            val appId = ctx.run(UUID_SERDE) {
                client.submitApplication(request.productId, request.userDetails, request.loanAmount)
            }

            // Stage 1: Identity Verification
            val identity = ctx.run(IDENTITY_SERDE) {
                val result = client.verifyIdentity(request.userDetails.emiratesId)
                client.notifyLos(appId, ApplicationEvent.StageCompleted("identity_verification", result))
                return@run result
            }

            // Stage 2: Credit Bureau
            val creditBureau = ctx.run(CREDIT_BUREAU_SERDE) {
                val result = client.fetchCreditBureau(request.userDetails.emiratesId, identity.verificationId)
                client.notifyLos(appId, ApplicationEvent.StageCompleted("credit_bureau", result))
                return@run result
            }

            // Stage 3: Open Banking (conditional)
            val openBanking = if (request.config.openBanking.enabled) {
                ctx.run(OPEN_BANKING_SERDE) {
                    val result = client.fetchOpenBanking(appId)
                    client.notifyLos(appId, ApplicationEvent.StageCompleted("open_banking", result))
                    return@run result
                }
            } else null

            // Stage 4: Employment Verification (conditional)
            if (request.config.employmentVerification.enabled) {
                ctx.run("employment_verification") {
                    val result = client.verifyEmployment(request.userDetails.emiratesId)
                    client.notifyLos(appId, ApplicationEvent.StageCompleted("employment_verification", result))
                }
            }

            // Stage 5: AML Screening (conditional)
            if (request.config.amlScreening.enabled) {
                ctx.run("aml_screening") {
                    val result = client.screenAml(appId, request.userDetails.emiratesId)
                    client.notifyLos(appId, ApplicationEvent.StageCompleted("aml_screening", result))
                }
            }

            // Stage 6: Fraud Scoring (conditional)
            val fraud = if (request.config.fraudScoring.enabled) {
                ctx.run(FRAUD_SCORE_SERDE) {
                    val result = client.scoreFraud(appId, creditBureau)
                    client.notifyLos(appId, ApplicationEvent.StageCompleted("fraud_scoring", result))
                    return@run result
                }
            } else FraudScore("n/a", 100, "LOW", 0)

            // Stage 7: Decision
            val decision = ctx.run(RISK_SCORE_SERDE) {
                val result = client.scoreApplication(creditBureau, openBanking, fraud, request.productId)
                client.notifyLos(appId, ApplicationEvent.DecisionMade(result))
                return@run result
            }

            // Stage 8: Disbursement Notification (conditional, only if approved)
            if (decision.outcome == RiskScore.Outcome.AUTO_APPROVE && request.config.disbursementNotification.enabled) {
                ctx.run("disbursement_notification") {
                    val result = client.notifyDisbursement(appId, request.loanAmount, "DEFAULT_ACCOUNT")
                    client.notifyLos(appId, ApplicationEvent.StageCompleted("disbursement_notification", result))
                }
            }

            // Return result based on decision
            when (decision.outcome) {
                RiskScore.Outcome.AUTO_APPROVE -> LoanApplicationResult(
                    applicationId = appId,
                    status = "approved",
                    approvedAmount = request.loanAmount,
                    decisionReason = "Auto-approved with score ${decision.score}"
                )
                RiskScore.Outcome.AUTO_REJECT -> LoanApplicationResult(
                    applicationId = appId,
                    status = "rejected",
                    approvedAmount = null,
                    decisionReason = "Auto-rejected with score ${decision.score}"
                )
                RiskScore.Outcome.MANUAL -> LoanApplicationResult(
                    applicationId = appId,
                    status = "rejected",
                    approvedAmount = null,
                    decisionReason = "Manual review required (score ${decision.score})"
                )
            }
        } catch (e: Exception) {
            LoanApplicationResult(
                applicationId = UUID.randomUUID(),
                status = "failed",
                approvedAmount = null,
                decisionReason = e.message ?: "Unknown error"
            )
        }
    }
}
