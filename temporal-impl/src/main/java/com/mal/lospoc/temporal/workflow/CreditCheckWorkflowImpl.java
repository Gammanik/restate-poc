package com.mal.lospoc.temporal.workflow;

import com.mal.lospoc.common.client.WorkflowClient;
import com.mal.lospoc.common.domain.ApplicationEvent;
import com.mal.lospoc.common.domain.LoanProductConfig;
import com.mal.lospoc.common.dto.*;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.UUID;

public class CreditCheckWorkflowImpl implements CreditCheckWorkflow {
    private static final Logger log = Workflow.getLogger(CreditCheckWorkflowImpl.class);

    private final Activities activities = Workflow.newActivityStub(
        Activities.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .build()
    );

    @Override
    public void run(CreditCheckRequest req) {
        UUID appId = req.applicationId();
        LoanProductConfig config = req.productConfig();

        log.info("[Temporal] Starting workflow for {}", appId);

        ConsentRecord consent = activities.consent(appId, req.httpbinUrl(), req.losUrl());
        AecbReport aecb = activities.aecb(appId, req.userDetails().emiratesId(), consent.consentRecordId(), req.httpbinUrl(), req.losUrl());

        OpenBankingSnapshot ob = null;
        if (config.openBanking().enabled()) {
            ob = activities.openBanking(appId, consent.consentRecordId(), req.httpbinUrl(), req.losUrl());
        }

        RiskScore decision = activities.decision(appId, aecb, ob, req.productId(), req.httpbinUrl(), req.losUrl());

        log.info("[Temporal] Workflow completed for {}: {}", appId, decision.outcome());
    }

    @ActivityInterface
    public interface Activities {
        ConsentRecord consent(UUID appId, String httpbinUrl, String losUrl);
        AecbReport aecb(UUID appId, String emiratesId, String consentId, String httpbinUrl, String losUrl);
        OpenBankingSnapshot openBanking(UUID appId, String consentId, String httpbinUrl, String losUrl);
        RiskScore decision(UUID appId, AecbReport aecb, OpenBankingSnapshot ob, String productId, String httpbinUrl, String losUrl);
    }

    public static class ActivitiesImpl implements Activities {
        @Override
        public ConsentRecord consent(UUID appId, String httpbinUrl, String losUrl) {
            WorkflowClient client = new WorkflowClient(httpbinUrl, losUrl);
            ConsentRecord result = client.captureConsent(appId);
            client.notifyLos(appId, new ApplicationEvent.StageCompleted("consent", result));
            return result;
        }

        @Override
        public AecbReport aecb(UUID appId, String emiratesId, String consentId, String httpbinUrl, String losUrl) {
            WorkflowClient client = new WorkflowClient(httpbinUrl, losUrl);
            AecbReport result = client.fetchAecb(emiratesId, consentId);
            client.notifyLos(appId, new ApplicationEvent.StageCompleted("aecb", result));
            return result;
        }

        @Override
        public OpenBankingSnapshot openBanking(UUID appId, String consentId, String httpbinUrl, String losUrl) {
            WorkflowClient client = new WorkflowClient(httpbinUrl, losUrl);
            OpenBankingSnapshot result = client.fetchOpenBanking(appId, consentId);
            client.notifyLos(appId, new ApplicationEvent.StageCompleted("open_banking", result));
            return result;
        }

        @Override
        public RiskScore decision(UUID appId, AecbReport aecb, OpenBankingSnapshot ob, String productId, String httpbinUrl, String losUrl) {
            WorkflowClient client = new WorkflowClient(httpbinUrl, losUrl);
            RiskScore result = client.scoreApplication(aecb, ob, productId);
            client.notifyLos(appId, new ApplicationEvent.DecisionMade(result));
            return result;
        }
    }
}
