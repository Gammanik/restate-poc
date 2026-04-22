package com.mal.lospoc.httpbin.controller;

import com.mal.lospoc.httpbin.service.FailureSimulator;
import com.mal.lospoc.httpbin.service.LatencySimulator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/credit-bureau")
public class CreditBureauController {
    private final LatencySimulator latency;
    private final FailureSimulator failure;
    private final Random random = new Random();

    @Value("${simulation.latency_ms:15}")
    private long latencyMs;

    @Value("${simulation.failure_rate:0.0}")
    private double failureRate;

    public CreditBureauController(LatencySimulator latency, FailureSimulator failure) {
        this.latency = latency;
        this.failure = failure;
    }

    @PostMapping("/report")
    public ResponseEntity<Map<String, Object>> fetchReport(@RequestBody Map<String, Object> request) {
        latency.simulate("credit_bureau", latencyMs);
        failure.maybeThrow("credit_bureau", failureRate);

        int score = 300 + random.nextInt(551); // 300-850
        return ResponseEntity.ok(Map.of(
            "bureauScore", score,
            "openLoans", random.nextInt(5),
            "defaultCount", random.nextInt(3),
            "inquiriesLast6M", random.nextInt(10)
        ));
    }
}
