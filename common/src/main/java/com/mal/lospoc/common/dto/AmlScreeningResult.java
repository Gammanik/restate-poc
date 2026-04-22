package com.mal.lospoc.common.dto;

public record AmlScreeningResult(
    String screeningId,
    String result,
    String riskLevel,
    int matchesFound
) {}
