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
@RequestMapping("/aml")
public class AmlScreeningController {
    private final LatencySimulator latency;
    private final FailureSimulator failure;
    private final Random random = new Random();

    @Value("${simulation.latency_ms:15}")
    private long latencyMs;

    @Value("${simulation.failure_rate:0.0}")
    private double failureRate;

    public AmlScreeningController(LatencySimulator latency, FailureSimulator failure) {
        this.latency = latency;
        this.failure = failure;
    }

    @PostMapping("/screen")
    public ResponseEntity<Map<String, Object>> screen(@RequestBody Map<String, Object> request) {
        latency.simulate("aml_screening", latencyMs);
        failure.maybeThrow("aml_screening", failureRate);

        String[] results = {"CLEAR", "CLEAR", "CLEAR", "CLEAR", "REVIEW_REQUIRED"};
        return ResponseEntity.ok(Map.of(
            "screeningId", UUID.randomUUID().toString(),
            "result", results[random.nextInt(results.length)],
            "riskLevel", "LOW",
            "matchesFound", 0
        ));
    }
}
