package com.mal.lospoc.common.dto;

public record RiskScore(
    int score,
    Outcome outcome
) {
    public enum Outcome {
        AUTO_APPROVE, AUTO_REJECT, MANUAL
    }
}
