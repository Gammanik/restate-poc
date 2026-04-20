package com.mal.lospoc.httpbin.controller;

import com.mal.lospoc.httpbin.service.FailureSimulator;
import com.mal.lospoc.httpbin.service.LatencySimulator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/delay")
public class AecbController {
    private final LatencySimulator latency;
    private final FailureSimulator failure;
    private final Random random = new Random();

    @Value("${simulation.failure_rate.aecb:0.10}")
    private double failureRate;

    public AecbController(LatencySimulator latency, FailureSimulator failure) {
        this.latency = latency;
        this.failure = failure;
    }

    @PostMapping("/{seconds}")
    public ResponseEntity<Map<String, Object>> fetchAecb(
        @PathVariable int seconds,
        @RequestBody Map<String, Object> request
    ) {
        latency.simulate("aecb", seconds * 1000L);
        failure.maybeThrow("aecb", failureRate);

        int score = 300 + random.nextInt(551); // 300-850
        return ResponseEntity.ok(Map.of(
            "bureauScore", score,
            "openLoans", random.nextInt(5),
            "defaultCount", random.nextInt(3),
            "inquiriesLast6M", random.nextInt(10)
        ));
    }
}
