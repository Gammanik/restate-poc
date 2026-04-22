package com.mal.lospoc.httpbin.controller;

import com.mal.lospoc.httpbin.service.FailureSimulator;
import com.mal.lospoc.httpbin.service.LatencySimulator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/core-banking")
public class DisbursementController {
    private final LatencySimulator latency;
    private final FailureSimulator failure;

    @Value("${simulation.latency_ms:15}")
    private long latencyMs;

    @Value("${simulation.failure_rate:0.0}")
    private double failureRate;

    public DisbursementController(LatencySimulator latency, FailureSimulator failure) {
        this.latency = latency;
        this.failure = failure;
    }

    @PostMapping("/disburse")
    public ResponseEntity<Map<String, Object>> disburse(@RequestBody Map<String, Object> request) {
        latency.simulate("disbursement", latencyMs);
        failure.maybeThrow("disbursement", failureRate);

        return ResponseEntity.ok(Map.of(
            "transactionId", UUID.randomUUID().toString(),
            "status", "SCHEDULED",
            "scheduledDate", Instant.now().plusSeconds(86400).toString(), // +1 day
            "amount", request.getOrDefault("amount", 0),
            "accountNumber", request.getOrDefault("accountNumber", "UNKNOWN")
        ));
    }
}
