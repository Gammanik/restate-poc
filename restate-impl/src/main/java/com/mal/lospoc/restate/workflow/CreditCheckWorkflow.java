package com.mal.lospoc.restate.workflow;

import com.mal.lospoc.common.domain.ApplicationEvent;
import com.mal.lospoc.common.domain.LoanProductConfig;
import com.mal.lospoc.common.dto.*;
import com.mal.lospoc.restate.client.ExternalServiceClient;
import com.mal.lospoc.restate.client.LosClient;
import dev.restate.sdk.Context;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

@Workflow
public class CreditCheckWorkflow {
    private static final Logger log = LoggerFactory.getLogger(CreditCheckWorkflow.class);

    @Handler
    public void run(Context ctx, CreditCheckRequest request) {
        UUID appId = request.applicationId();
        String productId = request.productId();
        LoanProductConfig config = request.productConfig();

        ExternalServiceClient externalClient = new ExternalServiceClient(request.httpbinUrl());
        LosClient losClient = new LosClient(request.losUrl());

        log.info("Starting credit check workflow for application: {}", appId);

        // Stage 1: User details collection (internal)
        ctx.run("collect-user-details", () -> {
            log.info("Collecting user details for {}", appId);
            losClient.applyEvent(appId, new ApplicationEvent.UserDetailsCollected(request.userDetails()));
        });

        // Stage 2: Consent capture
        ConsentRecord consent = ctx.run("capture-consent", ConsentRecord.class, () -> {
            log.info("Capturing consent for {}", appId);
            return externalClient.captureConsent(appId, List.of("AECB", "OpenBanking"));
        });
        ctx.run("apply-consent-event", () -> {
            losClient.applyEvent(appId, new ApplicationEvent.ConsentCaptured(consent));
        });

        // Stage 3: AECB fetch
        AecbReport aecb = ctx.run("fetch-aecb", AecbReport.class, () -> {
            log.info("Fetching AECB report for {}", appId);
            return externalClient.fetchAecb(request.userDetails().emiratesId(), consent.consentRecordId());
        });
        ctx.run("apply-aecb-event", () -> {
            losClient.applyEvent(appId, new ApplicationEvent.AecbFetched(aecb));
        });

        // Stage 4: Open Banking (conditional based on product config)
        OpenBankingSnapshot openBanking = null;
        if (config.openBanking().enabled()) {
            openBanking = ctx.run("fetch-open-banking", OpenBankingSnapshot.class, () -> {
                log.info("Fetching open banking snapshot for {}", appId);
                return externalClient.fetchOpenBanking(appId, consent.consentRecordId());
            });
            OpenBankingSnapshot finalOpenBanking = openBanking;
            ctx.run("apply-open-banking-event", () -> {
                losClient.applyEvent(appId, new ApplicationEvent.OpenBankingFetched(finalOpenBanking));
            });
        }

        // Stage 5: Decisioning
        OpenBankingSnapshot finalOpenBanking2 = openBanking;
        RiskScore decision = ctx.run("score-application", RiskScore.class, () -> {
            log.info("Scoring application {}", appId);
            return externalClient.scoreApplication(aecb, finalOpenBanking2, productId);
        });
        ctx.run("apply-decision-event", () -> {
            losClient.applyEvent(appId, new ApplicationEvent.DecisionMade(decision));
        });

        // Stage 6: Underwriting (if manual review required)
        if (decision.outcome() == RiskScore.Outcome.MANUAL) {
            log.info("Application {} requires manual underwriting - marking for review", appId);

            ctx.run("mark-for-underwriting", () -> {
                losClient.applyEvent(appId, new ApplicationEvent.UnderwritingStarted("system"));
            });

            // For this POC: underwriting awaitable will be added in phase 2
            // Workflows will pause here waiting for external signal
        }

        log.info("Credit check workflow completed for application: {}", appId);
    }

    public record CreditCheckRequest(
        UUID applicationId,
        String productId,
        UserDetails userDetails,
        LoanProductConfig productConfig,
        String httpbinUrl,
        String losUrl
    ) {}
}
