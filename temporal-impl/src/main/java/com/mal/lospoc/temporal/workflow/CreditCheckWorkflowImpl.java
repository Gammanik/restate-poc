package com.mal.lospoc.temporal.workflow;

import com.mal.lospoc.common.domain.LoanProductConfig;
import com.mal.lospoc.common.dto.*;
import com.mal.lospoc.temporal.activities.CreditCheckActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.UUID;

public class CreditCheckWorkflowImpl implements CreditCheckWorkflow {
    private static final Logger log = Workflow.getLogger(CreditCheckWorkflowImpl.class);

    private final CreditCheckActivities activities = Workflow.newActivityStub(
        CreditCheckActivities.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .build()
    );

    private UnderwritingDecision pendingDecision = null;

    @Override
    public void run(CreditCheckRequest request) {
        UUID appId = request.applicationId();
        String productId = request.productId();
        LoanProductConfig config = request.productConfig();

        log.info("Starting Temporal credit check workflow for application: {}", appId);

        // Stage 1: User details collection
        activities.collectUserDetails(appId, request.userDetails());

        // Stage 2: Consent capture
        ConsentRecord consent = activities.captureConsent(appId);

        // Stage 3: AECB fetch
        AecbReport aecb = activities.fetchAecb(appId, request.userDetails().emiratesId(), consent.consentRecordId());

        // Stage 4: Open Banking (conditional)
        OpenBankingSnapshot openBanking = null;
        if (config.openBanking().enabled()) {
            openBanking = activities.fetchOpenBanking(appId, consent.consentRecordId());
        }

        // Stage 5: Decisioning
        RiskScore decision = activities.scoreApplication(appId, aecb, openBanking, productId);

        // Stage 6: Underwriting (if manual review required)
        if (decision.outcome() == RiskScore.Outcome.MANUAL) {
            log.info("Application {} requires manual underwriting", appId);

            activities.markForUnderwriting(appId);

            Workflow.await(() -> pendingDecision != null);

            activities.applyUnderwritingDecision(appId, pendingDecision);
        }

        log.info("Temporal credit check workflow completed for application: {}", appId);
    }

    @Override
    public void underwriterDecision(UnderwritingDecision decision) {
        log.info("Received underwriter decision: {}", decision);
        this.pendingDecision = decision;
    }
}
