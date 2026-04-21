package com.mal.lospoc.common.dto;

public record AecbReport(
    int bureauScore,
    int openLoans,
    int defaultCount,
    int inquiriesLast6M
) {}
