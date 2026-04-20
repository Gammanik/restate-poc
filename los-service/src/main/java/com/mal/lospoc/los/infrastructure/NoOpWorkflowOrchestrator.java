package com.mal.lospoc.los.infrastructure;

import com.mal.lospoc.common.dto.UnderwritingDecision;
import com.mal.lospoc.los.application.port.WorkflowOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "workflow.engine", havingValue = "noop", matchIfMissing = false)
public class NoOpWorkflowOrchestrator implements WorkflowOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(NoOpWorkflowOrchestrator.class);

    @Override
    public void startCreditCheck(UUID applicationId, String productId) {
        log.warn("NoOp: startCreditCheck({}, {})", applicationId, productId);
    }

    @Override
    public void signalUnderwriterDecision(UUID applicationId, UnderwritingDecision decision) {
        log.warn("NoOp: signalUnderwriterDecision({}, {})", applicationId, decision);
    }

    @Override
    public void cancel(UUID applicationId) {
        log.warn("NoOp: cancel({})", applicationId);
    }
}
