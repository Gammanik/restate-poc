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
@RequestMapping("/mohre")
public class EmploymentVerificationController {
    private final LatencySimulator latency;
    private final FailureSimulator failure;
    private final Random random = new Random();

    @Value("${simulation.latency_ms:15}")
    private long latencyMs;

    @Value("${simulation.failure_rate:0.0}")
    private double failureRate;

    public EmploymentVerificationController(LatencySimulator latency, FailureSimulator failure) {
        this.latency = latency;
        this.failure = failure;
    }

    @PostMapping("/employment")
    public ResponseEntity<Map<String, Object>> verify(@RequestBody Map<String, Object> request) {
        latency.simulate("employment_verification", latencyMs);
        failure.maybeThrow("employment_verification", failureRate);

        String[] statuses = {"ACTIVE", "ACTIVE", "ACTIVE", "INACTIVE"};
        String[] types = {"FULL_TIME", "PART_TIME", "CONTRACT"};
        return ResponseEntity.ok(Map.of(
            "recordId", UUID.randomUUID().toString(),
            "employmentStatus", statuses[random.nextInt(statuses.length)],
            "employmentType", types[random.nextInt(types.length)],
            "tenureMonths", 6 + random.nextInt(120), // 6-126 months
            "monthlySalary", 5000 + random.nextInt(20000)
        ));
    }
}
