package com.mal.lospoc.httpbin.controller;

import com.mal.lospoc.httpbin.service.FailureSimulator;
import com.mal.lospoc.httpbin.service.LatencySimulator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/anything/consent-service")
public class ConsentController {
    private final LatencySimulator latency;
    private final FailureSimulator failure;

    @Value("${simulation.latency.consent:100}")
    private long latencyMs;

    @Value("${simulation.failure_rate.consent:0.05}")
    private double failureRate;

    public ConsentController(LatencySimulator latency, FailureSimulator failure) {
        this.latency = latency;
        this.failure = failure;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> captureConsent(@RequestBody Map<String, Object> request) {
        latency.simulate("consent", latencyMs);
        failure.maybeThrow("consent", failureRate);

        Instant now = Instant.now();
        return ResponseEntity.ok(Map.of(
            "consentRecordId", UUID.randomUUID().toString(),
            "consentTypes", request.getOrDefault("consentType", List.of("AECB", "OpenBanking")),
            "signedAt", now.toString(),
            "validUntil", now.plusSeconds(7776000).toString() // 90 days
        ));
    }
}
