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

    // Singleton HTTP client shared across all instances for connection pooling
    private static final OkHttpClient SHARED_HTTP_CLIENT = new OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .connectionPool(new ConnectionPool(200, 5, java.util.concurrent.TimeUnit.MINUTES))
        .build();

    private final OkHttpClient http = SHARED_HTTP_CLIENT;
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    private final String httpbinUrl;
    private final String losUrl;

    public WorkflowClient(String httpbinUrl, String losUrl) {
        this.httpbinUrl = httpbinUrl;
        this.losUrl = losUrl;
    }

    // External service calls (7 stages)
    public IdentityVerificationResult verifyIdentity(String emiratesId) {
        Map<String, Object> req = Map.of(
            "emiratesId", emiratesId,
            "idempotencyKey", UUID.randomUUID().toString()
        );
        Map<String, Object> res = post(httpbinUrl + "/emirates-id/verify", req);
        return new IdentityVerificationResult(
            (String) res.get("verificationId"),
            (String) res.get("status"),
            ((Number) res.get("matchScore")).intValue(),
            (String) res.get("emiratesId")
        );
    }

    public CreditBureauReport fetchCreditBureau(String emiratesId, String verificationId) {
        Map<String, Object> req = Map.of(
            "emiratesId", emiratesId,
            "verificationId", verificationId,
            "idempotencyKey", UUID.randomUUID().toString()
        );
        Map<String, Object> res = post(httpbinUrl + "/credit-bureau/report", req);
        return new CreditBureauReport(
            ((Number) res.get("bureauScore")).intValue(),
            ((Number) res.get("openLoans")).intValue(),
            ((Number) res.get("defaultCount")).intValue(),
            ((Number) res.get("inquiriesLast6M")).intValue()
        );
    }

    public OpenBankingSnapshot fetchOpenBanking(UUID appId) {
        Map<String, Object> req = Map.of(
            "applicantId", appId.toString()
        );
        Map<String, Object> res = post(httpbinUrl + "/anything/open-banking", req);
        return new OpenBankingSnapshot(
            java.math.BigDecimal.valueOf(((Number) res.get("monthlyIncome")).doubleValue()),
            java.math.BigDecimal.valueOf(((Number) res.get("avgBalance")).doubleValue()),
            java.math.BigDecimal.valueOf(((Number) res.get("monthlyExpenses")).doubleValue()),
            OpenBankingSnapshot.SalaryConsistency.valueOf((String) res.get("salaryConsistency"))
        );
    }

    public EmploymentRecord verifyEmployment(String emiratesId) {
        Map<String, Object> req = Map.of(
            "emiratesId", emiratesId,
            "idempotencyKey", UUID.randomUUID().toString()
        );
        Map<String, Object> res = post(httpbinUrl + "/mohre/employment", req);
        return new EmploymentRecord(
            (String) res.get("recordId"),
            (String) res.get("employmentStatus"),
            (String) res.get("employmentType"),
            ((Number) res.get("tenureMonths")).intValue(),
            ((Number) res.get("monthlySalary")).intValue()
        );
    }

    public AmlScreeningResult screenAml(UUID appId, String emiratesId) {
        Map<String, Object> req = Map.of(
            "applicantId", appId.toString(),
            "emiratesId", emiratesId,
            "idempotencyKey", UUID.randomUUID().toString()
        );
        Map<String, Object> res = post(httpbinUrl + "/aml/screen", req);
        return new AmlScreeningResult(
            (String) res.get("screeningId"),
            (String) res.get("result"),
            (String) res.get("riskLevel"),
            ((Number) res.get("matchesFound")).intValue()
        );
    }

    public FraudScore scoreFraud(UUID appId, CreditBureauReport creditReport) {
        Map<String, Object> req = Map.of(
            "applicantId", appId.toString(),
            "creditBureauScore", creditReport.bureauScore(),
            "idempotencyKey", UUID.randomUUID().toString()
        );
        Map<String, Object> res = post(httpbinUrl + "/fraud/score", req);
        return new FraudScore(
            (String) res.get("fraudCheckId"),
            ((Number) res.get("fraudScore")).intValue(),
            (String) res.get("riskLevel"),
            ((Number) res.get("flagsRaised")).intValue()
        );
    }

    public RiskScore scoreApplication(CreditBureauReport credit, OpenBankingSnapshot ob,
                                     FraudScore fraud, String productId) {
        Map<String, Object> req = Map.of(
            "creditReport", credit,
            "openBankingSnapshot", ob == null ? Map.of() : ob,
            "fraudScore", fraud,
            "productId", productId
        );
        Map<String, Object> res = post(httpbinUrl + "/anything/decision-engine", req);
        return new RiskScore(
            ((Number) res.get("score")).intValue(),
            RiskScore.Outcome.valueOf((String) res.get("outcome"))
        );
    }

    public DisbursementConfirmation notifyDisbursement(UUID appId, Object amount, String accountNumber) {
        Map<String, Object> req = Map.of(
            "applicantId", appId.toString(),
            "amount", amount,
            "accountNumber", accountNumber
        );
        Map<String, Object> res = post(httpbinUrl + "/core-banking/disburse", req);
        return new DisbursementConfirmation(
            (String) res.get("transactionId"),
            (String) res.get("status"),
            Instant.parse((String) res.get("scheduledDate")),
            res.get("amount"),
            (String) res.get("accountNumber")
        );
    }

    // LOS operations
    public UUID submitApplication(String productId, UserDetails userDetails, Object loanAmount) {
        Map<String, Object> req = Map.of(
            "productId", productId,
            "userDetails", userDetails,
            "loanAmount", loanAmount
        );
        Map<String, Object> res = post(losUrl + "/api/applications", req);
        return UUID.fromString((String) res.get("applicationId"));
    }

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
