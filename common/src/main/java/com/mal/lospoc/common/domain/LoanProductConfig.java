package com.mal.lospoc.common.domain;

import java.time.Duration;

public record LoanProductConfig(
    String productId,
    StageConfig identityVerification,
    StageConfig creditBureau,
    StageConfig openBanking,
    StageConfig employmentVerification,
    StageConfig amlScreening,
    StageConfig fraudScoring,
    StageConfig disbursementNotification,
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
