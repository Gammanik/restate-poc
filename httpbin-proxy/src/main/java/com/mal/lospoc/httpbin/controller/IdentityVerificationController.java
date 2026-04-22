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
@RequestMapping("/emirates-id")
public class IdentityVerificationController {
    private final LatencySimulator latency;
    private final FailureSimulator failure;
    private final Random random = new Random();

    @Value("${simulation.latency_ms:15}")
    private long latencyMs;

    @Value("${simulation.failure_rate:0.0}")
    private double failureRate;

    public IdentityVerificationController(LatencySimulator latency, FailureSimulator failure) {
        this.latency = latency;
        this.failure = failure;
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(@RequestBody Map<String, Object> request) {
        latency.simulate("identity_verification", latencyMs);
        failure.maybeThrow("identity_verification", failureRate);

        String[] statuses = {"VERIFIED", "VERIFIED", "VERIFIED", "PENDING"};
        return ResponseEntity.ok(Map.of(
            "verificationId", UUID.randomUUID().toString(),
            "status", statuses[random.nextInt(statuses.length)],
            "matchScore", 85 + random.nextInt(16), // 85-100
            "emiratesId", request.getOrDefault("emiratesId", "unknown")
        ));
    }
}
