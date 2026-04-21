package com.mal.lospoc.temporal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mal.lospoc.common.domain.LoanProductConfig;
import com.mal.lospoc.common.dto.UserDetails;
import com.mal.lospoc.temporal.workflow.CreditCheckWorkflow;
import com.mal.lospoc.temporal.workflow.CreditCheckWorkflowImpl;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

public class TemporalApp {
    public static final String TASK_QUEUE = "CREDIT_CHECK_QUEUE";
    private static final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final String LOS_URL = "http://localhost:8000";
    private static final String HTTPBIN_URL = "http://localhost:8090";

    public static void main(String[] args) throws IOException {
        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        WorkflowClient client = WorkflowClient.newInstance(service);
        WorkerFactory factory = WorkerFactory.newInstance(client);

        Worker worker = factory.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(CreditCheckWorkflowImpl.class);
        worker.registerActivitiesImplementations(new CreditCheckWorkflowImpl.ActivitiesImpl());

        factory.start();
        System.out.println("[Temporal] Worker started on queue: " + TASK_QUEUE);

        // HTTP server to trigger workflows (for benchmark)
        // Use larger backlog (1000) to handle high concurrent connections during benchmarks
        HttpServer server = HttpServer.create(new InetSocketAddress(8001), 1000);

        // Health check endpoint
        server.createContext("/", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, Map.of("status", "ok", "service", "temporal"));
            } else {
                sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            }
        });

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

                CreditCheckWorkflow workflow = client.newWorkflowStub(
                    CreditCheckWorkflow.class,
                    WorkflowOptions.newBuilder()
                        .setTaskQueue(TASK_QUEUE)
                        .setWorkflowId("credit-check-" + appId)
                        .build()
                );

                CreditCheckWorkflowImpl.CreditCheckRequest workflowReq =
                    new CreditCheckWorkflowImpl.CreditCheckRequest(
                        appId, req.productId, req.userDetails, config, HTTPBIN_URL, LOS_URL
                    );

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

        // Use a fixed thread pool to handle concurrent requests efficiently
        server.setExecutor(Executors.newFixedThreadPool(100));
        server.start();
        System.out.println("[Temporal] HTTP server started on port 8001");
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
