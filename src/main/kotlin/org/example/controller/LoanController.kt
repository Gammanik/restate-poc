package org.example.controller

import org.example.model.LoanApplication
import org.example.model.LoanResult
import org.example.workflow.LoanApplicationWorkflow
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.util.UUID

data class CheckCreditRequest(
    val applicantName: String,
    val amount: BigDecimal,
    val income: BigDecimal
)

@RestController
@RequestMapping("/api")
class LoanController(
    private val loanWorkflow: LoanApplicationWorkflow
) {
    private val logger = LoggerFactory.getLogger(LoanController::class.java)

    @PostMapping("/checkCredit")
    suspend fun checkCredit(@RequestBody request: CheckCreditRequest): LoanResult {
        val applicationId = UUID.randomUUID().toString().take(8)

        val application = LoanApplication(
            applicationId = applicationId,
            applicantName = request.applicantName,
            amount = request.amount,
            income = request.income
        )

        logger.info("Processing loan application via REST API: {}", application.applicationId)

        // In a real Restate setup, this would invoke the workflow via Restate client
        // For this POC, we'll call it directly
        val result = loanWorkflow.processApplication(
            // Note: Context would be provided by Restate runtime
            null as dev.restate.sdk.kotlin.Context, // Placeholder - needs proper Restate setup
            application
        )

        logger.info("Loan application {} completed with decision: {}", applicationId, result.decision)

        return result
    }
}
