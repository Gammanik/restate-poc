package com.mal.lospoc.temporal.workflow;

import com.mal.lospoc.common.domain.LoanProductConfig;
import com.mal.lospoc.common.dto.UnderwritingDecision;
import com.mal.lospoc.common.dto.UserDetails;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.util.UUID;

@WorkflowInterface
public interface CreditCheckWorkflow {

    @WorkflowMethod
    void run(CreditCheckRequest request);

    @SignalMethod
    void underwriterDecision(UnderwritingDecision decision);

    record CreditCheckRequest(
        UUID applicationId,
        String productId,
        UserDetails userDetails,
        LoanProductConfig productConfig,
        String httpbinUrl,
        String losUrl
    ) {}
}
