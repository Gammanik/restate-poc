package com.mal.lospoc.los.application.port;

import com.mal.lospoc.common.dto.UnderwritingDecision;

import java.util.UUID;

public interface WorkflowOrchestrator {
    void startCreditCheck(UUID applicationId, String productId);
    void signalUnderwriterDecision(UUID applicationId, UnderwritingDecision decision);
    void cancel(UUID applicationId);
}
