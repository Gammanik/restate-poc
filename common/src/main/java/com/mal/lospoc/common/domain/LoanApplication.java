package com.mal.lospoc.common.domain;

import com.mal.lospoc.common.dto.UserDetails;

import java.math.BigDecimal;
import java.util.UUID;

public record LoanApplication(
    UUID applicationId,
    String productId,
    UserDetails userDetails,
    BigDecimal loanAmount,
    ApplicationState state
) {
    public static LoanApplication initiate(String productId, UserDetails userDetails, BigDecimal loanAmount) {
        return new LoanApplication(
            UUID.randomUUID(),
            productId,
            userDetails,
            loanAmount,
            new ApplicationState.Initiated()
        );
    }

    public LoanApplication withState(ApplicationState newState) {
        return new LoanApplication(applicationId, productId, userDetails, loanAmount, newState);
    }
}
