package org.example.controller

import dev.restate.client.Client
import org.example.model.LoanApplication
import org.example.model.LoanResult
import org.example.workflow.LoanApplicationWorkflowClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
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
    @Value("\${restate.endpoint.url:http://localhost:8080}") private val restateUrl: String
) {
    private val logger = LoggerFactory.getLogger(LoanController::class.java)

    // Lazy initialization of Restate client
    private val restateClient: Client by lazy {
        Client.connect(restateUrl)
    }

    @PostMapping("/checkCredit")
    suspend fun checkCredit(@RequestBody request: CheckCreditRequest): LoanResult {
        val applicationId = UUID.randomUUID().toString().take(8)

        val application = LoanApplication(
            applicationId = applicationId,
            applicantName = request.applicantName,
            amount = request.amount,
            income = request.income
        )

        logger.info("Processing loan application via Restate: {}", application.applicationId)

        // Call workflow via Restate client
        val workflowClient = LoanApplicationWorkflowClient.fromClient(restateClient, applicationId)
        val result = workflowClient.processApplication(application)

        logger.info("Loan application {} completed with decision: {}", applicationId, result.decision)

        return result
    }
}
