@file:Suppress("DEPRECATION")

package org.example

import dev.restate.sdk.http.vertx.RestateHttpEndpointBuilder
import org.example.service.ContractGenerationService
import org.example.service.CreditCheckService
import org.example.service.DecisionService
import org.example.workflow.LoanApplicationWorkflow
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class LoanApplication

fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("LoanApplication")

    // Start Restate endpoint
    logger.info("Starting Restate endpoint on port 9080...")

    RestateHttpEndpointBuilder.builder()
        .bind(CreditCheckService())
        .bind(DecisionService())
        .bind(ContractGenerationService())
        .bind(LoanApplicationWorkflow())
        .buildAndListen(9080)

    logger.info("Restate endpoint started successfully on port 9080")

    // Start Spring Boot application
    logger.info("Starting Spring Boot application...")
    runApplication<LoanApplication>(*args)
}
