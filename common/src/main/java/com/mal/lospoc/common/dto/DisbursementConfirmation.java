package com.mal.lospoc.common.dto;

import java.time.Instant;

public record DisbursementConfirmation(
    String transactionId,
    String status,
    Instant scheduledDate,
    Object amount,
    String accountNumber
) {}
