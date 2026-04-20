package com.mal.lospoc.httpbin.controller;

import com.mal.lospoc.httpbin.service.FailureSimulator;
import com.mal.lospoc.httpbin.service.LatencySimulator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/anything/decision-engine")
public class DecisionEngineController {
    private final LatencySimulator latency;
    private final FailureSimulator failure;
    private final Random random = new Random();

    @Value("${simulation.latency.decision_engine:200}")
    private long latencyMs;

    @Value("${simulation.failure_rate.decision_engine:0.02}")
    private double failureRate;

    public DecisionEngineController(LatencySimulator latency, FailureSimulator failure) {
        this.latency = latency;
        this.failure = failure;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> score(@RequestBody Map<String, Object> request) {
        latency.simulate("decision_engine", latencyMs);
        failure.maybeThrow("decision_engine", failureRate);

        int score = random.nextInt(1001); // 0-1000
        String outcome;
        if (score >= 700) {
            outcome = "AUTO_APPROVE";
        } else if (score >= 500) {
            outcome = "MANUAL";
        } else {
            outcome = "AUTO_REJECT";
        }

        return ResponseEntity.ok(Map.of(
            "score", score,
            "outcome", outcome
        ));
    }
}
