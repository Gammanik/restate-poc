package com.mal.lospoc.httpbin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LatencySimulator {
    private static final Logger log = LoggerFactory.getLogger(LatencySimulator.class);

    public void simulate(String endpoint, long millis) {
        if (millis > 0) {
            try {
                log.debug("Simulating {}ms latency for {}", millis, endpoint);
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Latency simulation interrupted", e);
            }
        }
    }
}
