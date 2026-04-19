package org.example.service

import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Service
import dev.restate.sdk.kotlin.Context
import org.example.model.Decision
import org.example.model.DecisionRequest
import org.slf4j.LoggerFactory

@Service
class DecisionService {

    private val logger = LoggerFactory.getLogger(DecisionService::class.java)

    @Handler
    suspend fun makeDecision(ctx: Context, request: DecisionRequest): Decision {
        logger.info(
            "DecisionService: Evaluating application {} with credit score {}",
            request.application.applicationId,
            request.creditScore
        )

        // Decision logic matching Camunda/Temporal implementations:
        // >= 750: APPROVED
        // >= 600 && < 680: MANUAL_REVIEW
        // else: REJECTED
        val decision = when {
            request.creditScore >= 750 -> Decision.APPROVED
            request.creditScore >= 600 && request.creditScore < 680 -> Decision.MANUAL_REVIEW
            else -> Decision.REJECTED
        }

        logger.info(
            "DecisionService: Application {} decision: {}",
            request.application.applicationId,
            decision
        )

        return decision
    }
}
