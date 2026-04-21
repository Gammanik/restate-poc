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
import java.time.Duration
import java.util.UUID

data class CreditCheckRequest(
    val applicationId: UUID,
    val productId: String,
    val userDetails: UserDetails,
    val productConfig: LoanProductConfig,
    val httpbinUrl: String,
    val losUrl: String
)

@WorkflowInterface
interface CreditCheckWorkflow {
    @WorkflowMethod
    fun run(req: CreditCheckRequest)
}

class CreditCheckWorkflowImpl : CreditCheckWorkflow {

    private val activities = Workflow.newActivityStub(
        Activities::class.java,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setScheduleToCloseTimeout(Duration.ofMinutes(5))
            .setRetryOptions(
                RetryOptions.newBuilder()
                    .setInitialInterval(Duration.ofSeconds(1))
                    .setMaximumInterval(Duration.ofSeconds(30))
                    .setBackoffCoefficient(2.0)
                    .setMaximumAttempts(6)
                    .setDoNotRetry(
                        "java.lang.IllegalArgumentException",
                        // + свой класс для 4xx, см. ниже
                    )
                    .build()
            )
            .build()
    )

    override fun run(req: CreditCheckRequest) {
        val appId = req.applicationId
        val config = req.productConfig

        val consent = activities.consent(appId, req.httpbinUrl, req.losUrl)
        val aecb = activities.aecb(appId, req.userDetails.emiratesId, consent.consentRecordId, req.httpbinUrl, req.losUrl)

        val ob = if (config.openBanking.enabled) {
            activities.openBanking(appId, consent.consentRecordId, req.httpbinUrl, req.losUrl)
        } else {
            null
        }

        activities.decision(appId, aecb, ob, req.productId, req.httpbinUrl, req.losUrl)
    }

    @ActivityInterface
    interface Activities {
        fun consent(appId: UUID, httpbinUrl: String, losUrl: String): ConsentRecord
        fun aecb(appId: UUID, emiratesId: String, consentId: String, httpbinUrl: String, losUrl: String): AecbReport
        fun openBanking(appId: UUID, consentId: String, httpbinUrl: String, losUrl: String): OpenBankingSnapshot
        fun decision(appId: UUID, aecb: AecbReport, ob: OpenBankingSnapshot?, productId: String, httpbinUrl: String, losUrl: String): RiskScore
    }

    class ActivitiesImpl : Activities {
        override fun consent(appId: UUID, httpbinUrl: String, losUrl: String): ConsentRecord {
            val client = WorkflowClient(httpbinUrl, losUrl)
            val result = client.captureConsent(appId)
            client.notifyLos(appId, ApplicationEvent.StageCompleted("consent", result))
            return result
        }

        override fun aecb(appId: UUID, emiratesId: String, consentId: String, httpbinUrl: String, losUrl: String): AecbReport {
            val client = WorkflowClient(httpbinUrl, losUrl)
            val result = client.fetchAecb(emiratesId, consentId)
            client.notifyLos(appId, ApplicationEvent.StageCompleted("aecb", result))
            return result
        }

        override fun openBanking(appId: UUID, consentId: String, httpbinUrl: String, losUrl: String): OpenBankingSnapshot {
            val client = WorkflowClient(httpbinUrl, losUrl)
            val result = client.fetchOpenBanking(appId, consentId)
            client.notifyLos(appId, ApplicationEvent.StageCompleted("open_banking", result))
            return result
        }

        override fun decision(appId: UUID, aecb: AecbReport, ob: OpenBankingSnapshot?, productId: String, httpbinUrl: String, losUrl: String): RiskScore {
            val client = WorkflowClient(httpbinUrl, losUrl)
            val result = client.scoreApplication(aecb, ob, productId)
            client.notifyLos(appId, ApplicationEvent.DecisionMade(result))
            return result
        }
    }
}