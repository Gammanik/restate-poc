package com.mal.lospoc.common.dto;

public record FraudScore(
    String fraudCheckId,
    int fraudScore,
    String riskLevel,
    int flagsRaised
) {}
