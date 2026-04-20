package com.mal.lospoc.common.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mal.lospoc.common.domain.ApplicationEvent;
import com.mal.lospoc.common.dto.*;
import okhttp3.*;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared HTTP client for both Restate and Temporal implementations
 */
public class WorkflowClient {
    private static final MediaType JSON = MediaType.get("application/json");
    private final OkHttpClient http = new OkHttpClient();
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    private final String httpbinUrl;
    private final String losUrl;

    public WorkflowClient(String httpbinUrl, String losUrl) {
        this.httpbinUrl = httpbinUrl;
        this.losUrl = losUrl;
    }

    // External service calls
    public ConsentRecord captureConsent(UUID appId) {
        Map<String, Object> req = Map.of(
            "applicantId", appId.toString(),
            "consentType", List.of("AECB", "OpenBanking"),
            "timestamp", Instant.now().toString()
        );
        Map<String, Object> res = post(httpbinUrl + "/anything/consent-service", req);
        return new ConsentRecord(
            (String) res.get("consentRecordId"),
            (List<String>) res.get("consentTypes"),
            Instant.parse((String) res.get("signedAt")),
            Instant.parse((String) res.get("validUntil"))
        );
    }

    public AecbReport fetchAecb(String emiratesId, String consentId) {
        Map<String, Object> req = Map.of(
            "emiratesId", emiratesId,
            "consentRecordId", consentId,
            "idempotencyKey", UUID.randomUUID().toString()
        );
        Map<String, Object> res = post(httpbinUrl + "/delay/2", req);
        return new AecbReport(
            ((Number) res.get("bureauScore")).intValue(),
            ((Number) res.get("openLoans")).intValue(),
            ((Number) res.get("defaultCount")).intValue(),
            ((Number) res.get("inquiriesLast6M")).intValue()
        );
    }

    public OpenBankingSnapshot fetchOpenBanking(UUID appId, String consentId) {
        Map<String, Object> req = Map.of(
            "applicantId", appId.toString(),
            "consentRecordId", consentId
        );
        Map<String, Object> res = post(httpbinUrl + "/anything/open-banking", req);
        return new OpenBankingSnapshot(
            java.math.BigDecimal.valueOf(((Number) res.get("monthlyIncome")).doubleValue()),
            java.math.BigDecimal.valueOf(((Number) res.get("avgBalance")).doubleValue()),
            java.math.BigDecimal.valueOf(((Number) res.get("monthlyExpenses")).doubleValue()),
            OpenBankingSnapshot.SalaryConsistency.valueOf((String) res.get("salaryConsistency"))
        );
    }

    public RiskScore scoreApplication(AecbReport aecb, OpenBankingSnapshot ob, String productId) {
        Map<String, Object> req = Map.of(
            "aecbReport", aecb,
            "openBankingSnapshot", ob == null ? Map.of() : ob,
            "productId", productId
        );
        Map<String, Object> res = post(httpbinUrl + "/anything/decision-engine", req);
        return new RiskScore(
            ((Number) res.get("score")).intValue(),
            RiskScore.Outcome.valueOf((String) res.get("outcome"))
        );
    }

    // LOS event callback
    public void notifyLos(UUID appId, ApplicationEvent event) {
        post(losUrl + "/internal/applications/" + appId + "/events", event);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String url, Object body) {
        try {
            String jsonBody = json.writeValueAsString(body);
            RequestBody reqBody = RequestBody.create(jsonBody, JSON);
            Request req = new Request.Builder().url(url).post(reqBody).build();

            try (Response res = http.newCall(req).execute()) {
                if (!res.isSuccessful()) {
                    throw new RuntimeException("HTTP " + res.code() + ": " + url);
                }
                return json.readValue(res.body().string(), Map.class);
            }
        } catch (IOException e) {
            throw new RuntimeException("Request failed: " + url, e);
        }
    }
}
