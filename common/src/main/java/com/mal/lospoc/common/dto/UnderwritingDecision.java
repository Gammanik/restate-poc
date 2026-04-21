package com.mal.lospoc.common.dto;

public record UnderwritingDecision(
    boolean approved,
    String rejectionReason
) {}
