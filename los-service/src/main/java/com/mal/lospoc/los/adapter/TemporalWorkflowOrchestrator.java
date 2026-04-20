package com.mal.lospoc.los.adapter;

import com.mal.lospoc.common.domain.LoanApplication;
import com.mal.lospoc.common.domain.LoanProductConfig;
import com.mal.lospoc.common.dto.UnderwritingDecision;
import com.mal.lospoc.los.application.config.ProductConfigLoader;
import com.mal.lospoc.los.application.port.WorkflowOrchestrator;
import com.mal.lospoc.los.infrastructure.InMemoryApplicationStore;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "workflow.engine", havingValue = "temporal")
public class TemporalWorkflowOrchestrator implements WorkflowOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(TemporalWorkflowOrchestrator.class);
    private static final String TASK_QUEUE = "CREDIT_CHECK_QUEUE";

    private final WorkflowClient client;
    private final InMemoryApplicationStore store;
    private final ProductConfigLoader configLoader;
    private final String httpbinUrl;
    private final String losUrl;

    public TemporalWorkflowOrchestrator(
        @Value("${workflow.temporal.url}") String temporalUrl,
        @Value("${httpbin.url:http://localhost:8090}") String httpbinUrl,
        @Value("${server.port}") int losPort,
        InMemoryApplicationStore store,
        ProductConfigLoader configLoader
    ) {
        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        this.client = WorkflowClient.newInstance(service);
        this.httpbinUrl = httpbinUrl;
        this.losUrl = "http://localhost:" + losPort;
        this.store = store;
        this.configLoader = configLoader;
    }

    @Override
    public void startCreditCheck(UUID applicationId, String productId) {
        LoanApplication app = store.findById(applicationId)
            .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));

        LoanProductConfig config = configLoader.getConfig(productId);

        Object request = Map.of(
            "applicationId", applicationId.toString(),
            "productId", productId,
            "userDetails", app.userDetails(),
            "productConfig", config,
            "httpbinUrl", httpbinUrl,
            "losUrl", losUrl
        );

        log.info("Starting Temporal workflow for application: {}", applicationId);

        WorkflowOptions options = WorkflowOptions.newBuilder()
            .setWorkflowId(applicationId.toString())
            .setTaskQueue(TASK_QUEUE)
            .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE)
            .build();

        // Create stub dynamically - need to use reflection or define interface
        // For POC, simplified approach
        log.warn("Temporal workflow start is simplified for POC - full implementation requires workflow stub");
    }

    @Override
    public void signalUnderwriterDecision(UUID applicationId, UnderwritingDecision decision) {
        log.info("Sending underwriter decision to Temporal for application: {}", applicationId);
        log.warn("Temporal signal is simplified for POC - full implementation requires workflow stub");
    }

    @Override
    public void cancel(UUID applicationId) {
        log.warn("Cancel not implemented for Temporal workflow: {}", applicationId);
    }
}
