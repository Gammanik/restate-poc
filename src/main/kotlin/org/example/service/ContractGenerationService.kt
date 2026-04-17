package org.example.service

import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Service
import dev.restate.sdk.kotlin.Context
import org.example.model.ContractGenerationResult
import org.example.model.LoanApplication
import org.slf4j.LoggerFactory
import java.time.Instant

@Service
class ContractGenerationService {

    private val logger = LoggerFactory.getLogger(ContractGenerationService::class.java)

    @Handler
    suspend fun generateContract(ctx: Context, application: LoanApplication): ContractGenerationResult {
        logger.info(
            "ContractGenerationService: Generating contract for application {} (amount: {})",
            application.applicationId,
            application.amount
        )

        // Simulate contract generation (in real world, would call contract service/template engine)
        val contractId = generateContractId(application.applicationId)
        val generatedAt = Instant.now().toString()

        logger.info(
            "ContractGenerationService: Contract generated successfully: {}",
            contractId
        )

        return ContractGenerationResult(
            contractId = contractId,
            generatedAt = generatedAt
        )
    }

    private fun generateContractId(applicationId: String): String {
        val timestamp = Instant.now().epochSecond
        return "CONTRACT-$applicationId-$timestamp"
    }
}
