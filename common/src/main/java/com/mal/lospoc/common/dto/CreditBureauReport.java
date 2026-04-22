package com.mal.lospoc.common.dto;

public record CreditBureauReport(
    int bureauScore,
    int openLoans,
    int defaultCount,
    int inquiriesLast6M
) {}
