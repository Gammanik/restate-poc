package com.mal.lospoc.temporal.activities;

import com.mal.lospoc.common.domain.ApplicationEvent;
import com.mal.lospoc.common.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.*;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CreditCheckActivitiesImpl implements CreditCheckActivities {
    private static final MediaType JSON = MediaType.get("application/json");
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final String httpbinUrl;
    private final String losUrl;

    public CreditCheckActivitiesImpl(String httpbinUrl, String losUrl) {
        this.httpbinUrl = httpbinUrl;
        this.losUrl = losUrl;
    }

    @Override
    public void collectUserDetails(UUID applicationId, UserDetails userDetails) {
        applyEvent(applicationId, new ApplicationEvent.UserDetailsCollected(userDetails));
    }

    @Override
    public ConsentRecord captureConsent(UUID applicationId) {
        Map<String, Object> request = Map.of(
            "applicantId", applicationId.toString(),
            "consentType", List.of("AECB", "OpenBanking"),
            "timestamp", Instant.now().toString()
        );
        Map<String, Object> response = postToHttpbin("/anything/consent-service", request);

        ConsentRecord consent = new ConsentRecord(
            (String) response.get("consentRecordId"),
            (List<String>) response.get("consentTypes"),
            Instant.parse((String) response.get("signedAt")),
            Instant.parse((String) response.get("validUntil"))
        );

        applyEvent(applicationId, new ApplicationEvent.ConsentCaptured(consent));
        return consent;
    }

    @Override
    public AecbReport fetchAecb(UUID applicationId, String emiratesId, String consentRecordId) {
        Map<String, Object> request = Map.of(
            "emiratesId", emiratesId,
            "consentRecordId", consentRecordId,
            "idempotencyKey", UUID.randomUUID().toString()
        );
        Map<String, Object> response = postToHttpbin("/delay/2", request);

        AecbReport report = new AecbReport(
            ((Number) response.get("bureauScore")).intValue(),
            ((Number) response.get("openLoans")).intValue(),
            ((Number) response.get("defaultCount")).intValue(),
            ((Number) response.get("inquiriesLast6M")).intValue()
        );

        applyEvent(applicationId, new ApplicationEvent.AecbFetched(report));
        return report;
    }

    @Override
    public OpenBankingSnapshot fetchOpenBanking(UUID applicationId, String consentRecordId) {
        Map<String, Object> request = Map.of(
            "applicantId", applicationId.toString(),
            "consentRecordId", consentRecordId
        );
        Map<String, Object> response = postToHttpbin("/anything/open-banking", request);

        OpenBankingSnapshot snapshot = new OpenBankingSnapshot(
            java.math.BigDecimal.valueOf(((Number) response.get("monthlyIncome")).doubleValue()),
            java.math.BigDecimal.valueOf(((Number) response.get("avgBalance")).doubleValue()),
            java.math.BigDecimal.valueOf(((Number) response.get("monthlyExpenses")).doubleValue()),
            OpenBankingSnapshot.SalaryConsistency.valueOf((String) response.get("salaryConsistency"))
        );

        applyEvent(applicationId, new ApplicationEvent.OpenBankingFetched(snapshot));
        return snapshot;
    }

    @Override
    public RiskScore scoreApplication(UUID applicationId, AecbReport aecb, OpenBankingSnapshot openBanking, String productId) {
        Map<String, Object> request = Map.of(
            "aecbReport", aecb,
            "openBankingSnapshot", openBanking == null ? Map.of() : openBanking,
            "productId", productId
        );
        Map<String, Object> response = postToHttpbin("/anything/decision-engine", request);

        RiskScore score = new RiskScore(
            ((Number) response.get("score")).intValue(),
            RiskScore.Outcome.valueOf((String) response.get("outcome"))
        );

        applyEvent(applicationId, new ApplicationEvent.DecisionMade(score));
        return score;
    }

    @Override
    public void markForUnderwriting(UUID applicationId) {
        applyEvent(applicationId, new ApplicationEvent.UnderwritingStarted("system"));
    }

    @Override
    public void applyUnderwritingDecision(UUID applicationId, UnderwritingDecision decision) {
        applyEvent(applicationId, new ApplicationEvent.UnderwritingCompleted(decision));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postToHttpbin(String path, Object requestBody) {
        try {
            String json = mapper.writeValueAsString(requestBody);
            RequestBody body = RequestBody.create(json, JSON);
            Request request = new Request.Builder()
                .url(httpbinUrl + path)
                .post(body)
                .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("HTTP error: " + response.code());
                }
                return mapper.readValue(response.body().string(), Map.class);
            }
        } catch (IOException e) {
            throw new RuntimeException("HTTP request failed", e);
        }
    }

    private void applyEvent(UUID applicationId, ApplicationEvent event) {
        try {
            String json = mapper.writeValueAsString(event);
            RequestBody body = RequestBody.create(json, JSON);
            Request request = new Request.Builder()
                .url(losUrl + "/internal/applications/" + applicationId + "/events")
                .post(body)
                .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Failed to apply event: " + response.code());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to apply event", e);
        }
    }
}
