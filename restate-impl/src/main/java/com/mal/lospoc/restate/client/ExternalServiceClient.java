package com.mal.lospoc.restate.client;

import com.mal.lospoc.common.dto.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ExternalServiceClient extends HttpbinClient {

    public ExternalServiceClient(String baseUrl) {
        super(baseUrl);
    }

    public ConsentRecord captureConsent(UUID applicantId, List<String> consentTypes) {
        Map<String, Object> request = Map.of(
            "applicantId", applicantId.toString(),
            "consentType", consentTypes,
            "timestamp", Instant.now().toString()
        );

        Map<String, Object> response = postForMap("/anything/consent-service", request);
        return new ConsentRecord(
            (String) response.get("consentRecordId"),
            (List<String>) response.get("consentTypes"),
            Instant.parse((String) response.get("signedAt")),
            Instant.parse((String) response.get("validUntil"))
        );
    }

    public AecbReport fetchAecb(String emiratesId, String consentRecordId) {
        Map<String, Object> request = Map.of(
            "emiratesId", emiratesId,
            "consentRecordId", consentRecordId,
            "idempotencyKey", UUID.randomUUID().toString()
        );

        Map<String, Object> response = postForMap("/delay/2", request);
        return new AecbReport(
            ((Number) response.get("bureauScore")).intValue(),
            ((Number) response.get("openLoans")).intValue(),
            ((Number) response.get("defaultCount")).intValue(),
            ((Number) response.get("inquiriesLast6M")).intValue()
        );
    }

    public OpenBankingSnapshot fetchOpenBanking(UUID applicantId, String consentRecordId) {
        Map<String, Object> request = Map.of(
            "applicantId", applicantId.toString(),
            "consentRecordId", consentRecordId
        );

        Map<String, Object> response = postForMap("/anything/open-banking", request);
        return new OpenBankingSnapshot(
            java.math.BigDecimal.valueOf(((Number) response.get("monthlyIncome")).doubleValue()),
            java.math.BigDecimal.valueOf(((Number) response.get("avgBalance")).doubleValue()),
            java.math.BigDecimal.valueOf(((Number) response.get("monthlyExpenses")).doubleValue()),
            OpenBankingSnapshot.SalaryConsistency.valueOf((String) response.get("salaryConsistency"))
        );
    }

    public RiskScore scoreApplication(AecbReport aecb, OpenBankingSnapshot openBanking, String productId) {
        Map<String, Object> request = Map.of(
            "aecbReport", aecb,
            "openBankingSnapshot", openBanking,
            "productId", productId
        );

        Map<String, Object> response = postForMap("/anything/decision-engine", request);
        return new RiskScore(
            ((Number) response.get("score")).intValue(),
            RiskScore.Outcome.valueOf((String) response.get("outcome"))
        );
    }
}
