package com.mal.lospoc.los.application.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class ApplicationStatsService {
    private final AtomicLong submitted = new AtomicLong(0);
    private final AtomicLong approved = new AtomicLong(0);
    private final AtomicLong rejected = new AtomicLong(0);
    private final AtomicLong failed = new AtomicLong(0);
    private final AtomicLong inFlight = new AtomicLong(0);

    public void incrementSubmitted() {
        submitted.incrementAndGet();
        inFlight.incrementAndGet();
    }

    public void incrementApproved() {
        approved.incrementAndGet();
        inFlight.decrementAndGet();
    }

    public void incrementRejected() {
        rejected.incrementAndGet();
        inFlight.decrementAndGet();
    }

    public void incrementFailed() {
        failed.incrementAndGet();
        inFlight.decrementAndGet();
    }

    public Stats getStats() {
        return new Stats(
            submitted.get(),
            approved.get(),
            rejected.get(),
            failed.get(),
            inFlight.get()
        );
    }

    public void reset() {
        submitted.set(0);
        approved.set(0);
        rejected.set(0);
        failed.set(0);
        inFlight.set(0);
    }

    public record Stats(
        long submitted,
        long approved,
        long rejected,
        long failed,
        long inFlight
    ) {}
}
