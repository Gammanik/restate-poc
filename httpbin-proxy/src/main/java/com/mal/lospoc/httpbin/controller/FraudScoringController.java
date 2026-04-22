package com.mal.lospoc.httpbin.controller;

import com.mal.lospoc.httpbin.service.FailureSimulator;
import com.mal.lospoc.httpbin.service.LatencySimulator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Random;
import java.util.UUID;

@RestController
@RequestMapping("/fraud")
public class FraudScoringController {
    private final LatencySimulator latency;
    private final FailureSimulator failure;
    private final Random random = new Random();

    @Value("${simulation.latency_ms:15}")
    private long latencyMs;

    @Value("${simulation.failure_rate:0.0}")
    private double failureRate;

    public FraudScoringController(LatencySimulator latency, FailureSimulator failure) {
        this.latency = latency;
        this.failure = failure;
    }

    @PostMapping("/score")
    public ResponseEntity<Map<String, Object>> score(@RequestBody Map<String, Object> request) {
        latency.simulate("fraud_scoring", latencyMs);
        failure.maybeThrow("fraud_scoring", failureRate);

        int score = random.nextInt(101); // 0-100
        String riskLevel;
        if (score >= 80) {
            riskLevel = "LOW";
        } else if (score >= 50) {
            riskLevel = "MEDIUM";
        } else {
            riskLevel = "HIGH";
        }

        return ResponseEntity.ok(Map.of(
            "fraudCheckId", UUID.randomUUID().toString(),
            "fraudScore", score,
            "riskLevel", riskLevel,
            "flagsRaised", score < 50 ? random.nextInt(3) + 1 : 0
        ));
    }
}
