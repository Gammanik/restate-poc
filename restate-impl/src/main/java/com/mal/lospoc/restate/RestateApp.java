package com.mal.lospoc.restate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mal.lospoc.common.client.WorkflowClient;
import com.mal.lospoc.common.domain.ApplicationEvent;
import com.mal.lospoc.common.domain.LoanProductConfig;
import com.mal.lospoc.common.dto.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Simplified Restate implementation using direct HTTP calls
 * For POC - demonstrates workflow orchestration without complex SDK setup
 */
public class RestateApp {
    private static final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final String LOS_URL = "http://localhost:8000";
    private static final String HTTPBIN_URL = "http://localhost:8091";

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);

        // Health check
        server.createContext("/", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, Map.of("status", "ok", "service", "restate"));
            } else {
                sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            }
        });

        // Application submission endpoint
        server.createContext("/api/applications", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
                return;
            }

            try {
                String body = new String(exchange.getRequestBody().readAllBytes());
                SubmitRequest req = json.readValue(body, SubmitRequest.class);

                UUID appId = UUID.randomUUID();
                LoanProductConfig config = createDefaultConfig(req.productId);

                // Execute workflow asynchronously
                CompletableFuture.runAsync(() -> {
                    try {
                        executeWorkflow(appId, req.productId, req.userDetails, config);
                    } catch (Exception e) {
                        // Workflow failed silently
                    }
                });

                sendResponse(exchange, 200, Map.of(
                    "applicationId", appId.toString(),
                    "status", "submitted"
                ));

            } catch (Exception e) {
                sendResponse(exchange, 500, Map.of("error", e.getMessage()));
            }
        });

        server.setExecutor(null);
        server.start();
        System.out.println("Restate HTTP server started successfully on port 8000");
    }

    private static void executeWorkflow(UUID appId, String productId, UserDetails userDetails, LoanProductConfig config) {
        WorkflowClient client = new WorkflowClient(HTTPBIN_URL, LOS_URL);

        ConsentRecord consent = client.captureConsent(appId);
        client.notifyLos(appId, new ApplicationEvent.StageCompleted("consent", consent));

        AecbReport aecb = client.fetchAecb(userDetails.emiratesId(), consent.consentRecordId());
        client.notifyLos(appId, new ApplicationEvent.StageCompleted("aecb", aecb));

        OpenBankingSnapshot ob = null;
        if (config.openBanking().enabled()) {
            ob = client.fetchOpenBanking(appId, consent.consentRecordId());
            client.notifyLos(appId, new ApplicationEvent.StageCompleted("open_banking", ob));
        }

        RiskScore decision = client.scoreApplication(aecb, ob, productId);
        client.notifyLos(appId, new ApplicationEvent.DecisionMade(decision));
    }

    private static void sendResponse(HttpExchange exchange, int code, Object body) throws IOException {
        String response = json.writeValueAsString(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, response.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    private static LoanProductConfig createDefaultConfig(String productId) {
        LoanProductConfig.StageConfig enabled = new LoanProductConfig.StageConfig(true, 30, 3);
        LoanProductConfig.DecisionThresholds thresholds = new LoanProductConfig.DecisionThresholds(700, 500);

        return new LoanProductConfig(
            productId,
            enabled,  // consent
            enabled,  // aecb
            enabled,  // openBanking
            enabled,  // decisioning
            thresholds,
            java.time.Duration.ofMinutes(10)
        );
    }

    record SubmitRequest(String productId, UserDetails userDetails, BigDecimal loanAmount) {}
}
