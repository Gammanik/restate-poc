package org.example.service

import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Service
import dev.restate.sdk.kotlin.Context
import org.example.model.CreditCheckResult
import org.example.model.LoanApplication
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.random.Random

@Service
class CreditCheckService {

    private val logger = LoggerFactory.getLogger(CreditCheckService::class.java)

    // Track attempts per application for demo purposes (simulating flaky behavior)
    private val attemptCounter = mutableMapOf<String, Int>()

    @Handler
    suspend fun checkCredit(ctx: Context, application: LoanApplication): CreditCheckResult {
        val attempts = attemptCounter.compute(application.applicationId) { _, v -> (v ?: 0) + 1 }!!

        logger.info(
            "CreditCheckService: Attempt {} for application {}",
            attempts,
            application.applicationId
        )

        // 30% chance of failure on first attempt to demonstrate retries
        if (attempts == 1 && Random.nextDouble() < 0.3) {
            logger.warn(
                "CreditCheckService: Simulating failure for application {} (will retry)",
                application.applicationId
            )
            throw RuntimeException("Simulated credit check service failure - please retry")
        }

        // Generate a credit score based on income/amount ratio for demo consistency
        val ratio = application.income.toDouble() / application.amount.toDouble()
        val baseScore = when {
            ratio >= 5.0 -> Random.nextInt(750, 850)
            ratio >= 3.0 -> Random.nextInt(650, 750)
            ratio >= 2.0 -> Random.nextInt(550, 650)
            ratio >= 1.0 -> Random.nextInt(450, 550)
            else -> Random.nextInt(300, 450)
        }

        val result = CreditCheckResult(
            score = baseScore,
            reportId = UUID.randomUUID().toString()
        )

        logger.info(
            "CreditCheckService: Completed for application {}, score: {}",
            application.applicationId,
            result.score
        )

        return result
    }
}
