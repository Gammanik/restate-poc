package com.mal.lospoc.httpbin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/config")
public class ConfigController {
    @Autowired
    private ConfigurableEnvironment env;

    @PutMapping("/failure-rate/{endpoint}")
    public ResponseEntity<Map<String, String>> setFailureRate(
        @PathVariable String endpoint,
        @RequestParam double rate
    ) {
        // Note: This is a simplified demo version. In production, use Spring Cloud Config or similar.
        String property = "simulation.failure_rate." + endpoint;
        System.setProperty(property, String.valueOf(rate));
        return ResponseEntity.ok(Map.of(
            "endpoint", endpoint,
            "failureRate", String.valueOf(rate),
            "status", "updated (restart required for effect)"
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
            "service", "httpbin-proxy",
            "status", "running",
            "endpoints", new String[]{
                "/anything/consent-service",
                "/delay/{seconds}",
                "/anything/open-banking",
                "/anything/decision-engine"
            }
        ));
    }
}
