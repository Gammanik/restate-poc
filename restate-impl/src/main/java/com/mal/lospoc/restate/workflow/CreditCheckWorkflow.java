package com.mal.lospoc.restate.workflow;

import com.mal.lospoc.common.client.WorkflowClient;
import com.mal.lospoc.common.domain.ApplicationEvent;
import com.mal.lospoc.common.domain.LoanProductConfig;
import com.mal.lospoc.common.dto.*;
import dev.restate.sdk.Context;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Workflow
public class CreditCheckWorkflow {
    private static final Logger log = LoggerFactory.getLogger(CreditCheckWorkflow.class);

    @Handler
    public void run(Context ctx, CreditCheckRequest req) {
        UUID appId = req.applicationId();
        LoanProductConfig config = req.productConfig();
        WorkflowClient client = new WorkflowClient(req.httpbinUrl(), req.losUrl());

        log.info("[Restate] Starting workflow for {}", appId);

        // Stage 1: Consent
        ConsentRecord consent = ctx.run("consent", ConsentRecord.class, () -> client.captureConsent(appId));
        ctx.run("notify-consent", () -> client.notifyLos(appId, new ApplicationEvent.StageCompleted("consent", consent)));

        // Stage 2: AECB
        AecbReport aecb = ctx.run("aecb", AecbReport.class, () ->
            client.fetchAecb(req.userDetails().emiratesId(), consent.consentRecordId()));
        ctx.run("notify-aecb", () -> client.notifyLos(appId, new ApplicationEvent.StageCompleted("aecb", aecb)));

        // Stage 3: Open Banking (conditional)
        OpenBankingSnapshot ob = null;
        if (config.openBanking().enabled()) {
            ob = ctx.run("open-banking", OpenBankingSnapshot.class, () ->
                client.fetchOpenBanking(appId, consent.consentRecordId()));
            OpenBankingSnapshot finalOb = ob;
            ctx.run("notify-ob", () -> client.notifyLos(appId, new ApplicationEvent.StageCompleted("open_banking", finalOb)));
        }

        // Stage 4: Decisioning
        OpenBankingSnapshot finalOb2 = ob;
        RiskScore decision = ctx.run("decision", RiskScore.class, () ->
            client.scoreApplication(aecb, finalOb2, req.productId()));
        ctx.run("notify-decision", () -> client.notifyLos(appId, new ApplicationEvent.DecisionMade(decision)));

        log.info("[Restate] Workflow completed for {}: {}", appId, decision.outcome());
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
