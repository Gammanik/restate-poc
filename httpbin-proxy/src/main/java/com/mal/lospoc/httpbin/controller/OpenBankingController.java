package com.mal.lospoc.httpbin.controller;

import com.mal.lospoc.httpbin.service.FailureSimulator;
import com.mal.lospoc.httpbin.service.LatencySimulator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/anything/open-banking")
public class OpenBankingController {
    private final LatencySimulator latency;
    private final FailureSimulator failure;
    private final Random random = new Random();

    @Value("${simulation.latency.open_banking:500}")
    private long latencyMs;

    @Value("${simulation.failure_rate.open_banking:0.05}")
    private double failureRate;

    public OpenBankingController(LatencySimulator latency, FailureSimulator failure) {
        this.latency = latency;
        this.failure = failure;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> snapshot(@RequestBody Map<String, Object> request) {
        latency.simulate("open_banking", latencyMs);
        failure.maybeThrow("open_banking", failureRate);

        String[] consistencies = {"CONSISTENT", "VARIABLE", "IRREGULAR"};
        return ResponseEntity.ok(Map.of(
            "monthlyIncome", 5000 + random.nextInt(15000),
            "avgBalance", 1000 + random.nextInt(50000),
            "monthlyExpenses", 2000 + random.nextInt(8000),
            "salaryConsistency", consistencies[random.nextInt(consistencies.length)]
        ));
    }
}
