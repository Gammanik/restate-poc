package org.example.model

import java.math.BigDecimal

data class LoanApplication(
    val applicationId: String,
    val applicantName: String,
    val amount: BigDecimal,
    val income: BigDecimal
)

data class CreditCheckResult(
    val score: Int,       // 300-850
    val reportId: String
)

enum class Decision {
    APPROVED,
    REJECTED,
    MANUAL_REVIEW
}

data class LoanResult(
    val applicationId: String,
    val decision: Decision,
    val creditScore: Int,
    val message: String,
    val contractId: String? = null
)

data class ContractGenerationResult(
    val contractId: String,
    val generatedAt: String
)
