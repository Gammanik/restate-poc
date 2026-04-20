package com.mal.lospoc.adapter;

import com.mal.lospoc.common.dto.UserDetails;
import com.mal.lospoc.common.domain.LoanProductConfig;
import com.mal.lospoc.temporal.workflow.CreditCheckWorkflow;
import com.mal.lospoc.temporal.workflow.CreditCheckWorkflowImpl;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP adapter that receives application requests and starts Temporal workflows
 */
public class TemporalAdapter {
    private static final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final String LOS_URL = "http://localhost:8000";
    private static final String HTTPBIN_URL = "http://localhost:8090";

    public static void main(String[] args) throws IOException {
        // Connect to Temporal
        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        WorkflowClient client = WorkflowClient.newInstance(service);

        // Start HTTP server on port 8001
        HttpServer server = HttpServer.create(new InetSocketAddress(8001), 0);

        server.createContext("/api/applications", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
                return;
            }

            try {
                String body = new String(exchange.getRequestBody().readAllBytes());
                SubmitRequest req = json.readValue(body, SubmitRequest.class);

                UUID appId = UUID.randomUUID();

                // Load product config (simplified - just create default)
                LoanProductConfig config = createDefaultConfig(req.productId);

                // Start Temporal workflow
                CreditCheckWorkflow workflow = client.newWorkflowStub(
                    CreditCheckWorkflow.class,
                    WorkflowOptions.newBuilder()
                        .setTaskQueue("CREDIT_CHECK_QUEUE")
                        .setWorkflowId("credit-check-" + appId)
                        .build()
                );

                CreditCheckWorkflowImpl.CreditCheckRequest workflowReq =
                    new CreditCheckWorkflowImpl.CreditCheckRequest(
                        appId, req.productId, req.userDetails, config, HTTPBIN_URL, LOS_URL
                    );

                // Start async
                WorkflowClient.start(workflow::run, workflowReq);

                sendResponse(exchange, 200, Map.of(
                    "applicationId", appId.toString(),
                    "status", "submitted"
                ));

            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, Map.of("error", e.getMessage()));
            }
        });

        server.setExecutor(null);
        server.start();
        System.out.println("[Temporal Adapter] Started on port 8001");
    }

    private static void sendResponse(HttpExchange exchange, int code, Object body) throws IOException {
        String response = json.writeValueAsString(body);
        exchange.sendResponseHeaders(code, response.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    private static LoanProductConfig createDefaultConfig(String productId) {
        return new LoanProductConfig(
            productId,
            new LoanProductConfig.OpenBankingStageConfig(true),
            new LoanProductConfig.DecisionConfig(700, 500)
        );
    }

    record SubmitRequest(String productId, UserDetails userDetails, BigDecimal loanAmount) {}
}
