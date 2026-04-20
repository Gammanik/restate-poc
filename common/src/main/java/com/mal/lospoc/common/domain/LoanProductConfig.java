package com.mal.lospoc.common.domain;

import java.time.Duration;

public record LoanProductConfig(
    String productId,
    StageConfig consent,
    StageConfig aecb,
    StageConfig openBanking,
    StageConfig decisioning,
    DecisionThresholds decision,
    Duration underwritingSla
) {
    public record StageConfig(
        boolean enabled,
        int timeoutSeconds,
        int retries
    ) {}

    public record DecisionThresholds(
        int autoApproveScore,
        int autoRejectScore
    ) {}
}
