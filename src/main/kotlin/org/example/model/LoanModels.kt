package org.example.model

import kotlinx.serialization.Serializable
import org.example.serialization.BigDecimalSerializer
import java.math.BigDecimal

@Serializable
data class LoanApplication(
    val applicationId: String,
    val applicantName: String,
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class) val income: BigDecimal
)

@Serializable
data class CreditCheckResult(
    val score: Int,       // 300-850
    val reportId: String
)

@Serializable
enum class Decision {
    APPROVED,
    REJECTED,
    MANUAL_REVIEW
}

@Serializable
data class LoanResult(
    val applicationId: String,
    val decision: Decision,
    val creditScore: Int,
    val message: String,
    val contractId: String? = null
)

@Serializable
data class ContractGenerationResult(
    val contractId: String,
    val generatedAt: String
)

@Serializable
data class DecisionRequest(
    val application: LoanApplication,
    val creditScore: Int
)
