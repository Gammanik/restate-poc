package org.example.service

import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Service
import dev.restate.sdk.kotlin.Context
import org.example.model.Decision
import org.example.model.LoanApplication
import org.slf4j.LoggerFactory

@Service
class DecisionService {

    private val logger = LoggerFactory.getLogger(DecisionService::class.java)

    @Handler
    suspend fun makeDecision(ctx: Context, application: LoanApplication, creditScore: Int): Decision {
        logger.info(
            "DecisionService: Evaluating application {} with credit score {}",
            application.applicationId,
            creditScore
        )

        // Decision logic matching Camunda/Temporal implementations:
        // >= 750: APPROVED
        // >= 600 && < 680: MANUAL_REVIEW
        // else: REJECTED
        val decision = when {
            creditScore >= 750 -> Decision.APPROVED
            creditScore >= 600 && creditScore < 680 -> Decision.MANUAL_REVIEW
            else -> Decision.REJECTED
        }

        logger.info(
            "DecisionService: Application {} decision: {}",
            application.applicationId,
            decision
        )

        return decision
    }
}
