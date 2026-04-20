package com.mal.lospoc.los.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mal.lospoc.common.domain.LoanApplication;
import com.mal.lospoc.common.domain.LoanProductConfig;
import com.mal.lospoc.common.dto.UnderwritingDecision;
import com.mal.lospoc.los.application.config.ProductConfigLoader;
import com.mal.lospoc.los.application.port.WorkflowOrchestrator;
import com.mal.lospoc.los.infrastructure.InMemoryApplicationStore;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "workflow.engine", havingValue = "restate")
public class RestateWorkflowOrchestrator implements WorkflowOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(RestateWorkflowOrchestrator.class);
    private static final MediaType JSON = MediaType.get("application/json");

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final InMemoryApplicationStore store;
    private final ProductConfigLoader configLoader;
    private final String restateUrl;
    private final String httpbinUrl;
    private final String losUrl;

    public RestateWorkflowOrchestrator(
        @Value("${workflow.restate.url}") String restateUrl,
        @Value("${httpbin.url:http://localhost:8090}") String httpbinUrl,
        @Value("${server.port}") int losPort,
        InMemoryApplicationStore store,
        ProductConfigLoader configLoader
    ) {
        this.restateUrl = restateUrl;
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

        Map<String, Object> request = Map.of(
            "applicationId", applicationId.toString(),
            "productId", productId,
            "userDetails", app.userDetails(),
            "productConfig", config,
            "httpbinUrl", httpbinUrl,
            "losUrl", losUrl
        );

        log.info("Starting Restate workflow for application: {}", applicationId);

        try {
            String json = mapper.writeValueAsString(request);
            RequestBody body = RequestBody.create(json, JSON);
            Request httpRequest = new Request.Builder()
                .url(restateUrl + "/CreditCheckWorkflow/" + applicationId + "/run/send")
                .post(body)
                .build();

            try (Response response = client.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Failed to start workflow: " + response.code());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to invoke Restate", e);
        }
    }

    @Override
    public void signalUnderwriterDecision(UUID applicationId, UnderwritingDecision decision) {
        log.info("Sending underwriter decision to Restate for application: {}", applicationId);

        try {
            String json = mapper.writeValueAsString(decision);
            RequestBody body = RequestBody.create(json, JSON);
            Request request = new Request.Builder()
                .url(restateUrl + "/CreditCheckWorkflow/" + applicationId + "/underwriterDecision/send")
                .post(body)
                .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Failed to signal decision: " + response.code());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to signal Restate", e);
        }
    }

    @Override
    public void cancel(UUID applicationId) {
        log.warn("Cancel not implemented for Restate workflow: {}", applicationId);
    }
}
