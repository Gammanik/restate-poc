package com.mal.lospoc.common.dto;

public record IdentityVerificationResult(
    String verificationId,
    String status,
    int matchScore,
    String emiratesId
) {}
