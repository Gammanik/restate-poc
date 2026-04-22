package com.mal.lospoc.restate

import com.mal.lospoc.restate.workflow.LoanApplicationService as KotlinService
import dev.restate.sdk.http.vertx.RestateHttpEndpointBuilder

/**
 * Restate HTTP endpoint that registers service handlers.
 * Runs on port 9080 - this is the deployment endpoint for Restate Server.
 */
fun main() {
    RestateHttpEndpointBuilder
        .builder()
        .bind(KotlinService())
        .buildAndListen(9080)

    println("Restate endpoint started on port 9080")
    println("Register with: curl -X POST http://localhost:9070/deployments -H 'Content-Type: application/json' -d '{\"uri\":\"http://host.docker.internal:9080\"}'")
}
