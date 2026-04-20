package com.mal.lospoc.httpbin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class FailureSimulator {
    private static final Logger log = LoggerFactory.getLogger(FailureSimulator.class);
    private final Random random = new Random();

    public void maybeThrow(String endpoint, double failureRate) {
        if (random.nextDouble() < failureRate) {
            log.warn("Simulated failure for {} (rate: {})", endpoint, failureRate);
            throw new SimulatedFailureException("Simulated failure for " + endpoint);
        }
    }

    public static class SimulatedFailureException extends RuntimeException {
        public SimulatedFailureException(String message) {
            super(message);
        }
    }
}
