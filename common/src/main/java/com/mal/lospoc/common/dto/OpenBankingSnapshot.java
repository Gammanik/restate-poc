package com.mal.lospoc.common.dto;

import java.math.BigDecimal;

public record OpenBankingSnapshot(
    BigDecimal monthlyIncome,
    BigDecimal avgBalance,
    BigDecimal monthlyExpenses,
    SalaryConsistency salaryConsistency
) {
    public enum SalaryConsistency {
        CONSISTENT, VARIABLE, IRREGULAR
    }
}
