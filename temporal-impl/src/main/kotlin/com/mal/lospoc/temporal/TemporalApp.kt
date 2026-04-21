package com.mal.lospoc.temporal

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.mal.lospoc.common.domain.LoanProductConfig
import com.mal.lospoc.common.dto.UserDetails
import com.mal.lospoc.temporal.workflow.CreditCheckRequest
import com.mal.lospoc.temporal.workflow.CreditCheckWorkflow
import com.mal.lospoc.temporal.workflow.CreditCheckWorkflowImpl
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.worker.WorkerFactory
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executors

const val TASK_QUEUE = "CREDIT_CHECK_QUEUE"
private val json = ObjectMapper().registerModule(JavaTimeModule())
private const val LOS_URL = "http://localhost:8000"
private const val HTTPBIN_URL = "http://localhost:8091"

data class SubmitRequest(
    val productId: String,
    val userDetails: UserDetails,
    val loanAmount: BigDecimal
)

fun main() {
    val service = WorkflowServiceStubs.newLocalServiceStubs()
    val client = WorkflowClient.newInstance(service)
    val factory = WorkerFactory.newInstance(client)

    val worker = factory.newWorker(TASK_QUEUE)
    worker.registerWorkflowImplementationTypes(CreditCheckWorkflowImpl::class.java)
    worker.registerActivitiesImplementations(CreditCheckWorkflowImpl.ActivitiesImpl())

    factory.start()

    val server = HttpServer.create(InetSocketAddress(8001), 1000)

    server.createContext("/") { exchange ->
        if (exchange.requestMethod == "GET") {
            sendResponse(exchange, 200, mapOf("status" to "ok", "service" to "temporal"))
        } else {
            sendResponse(exchange, 405, mapOf("error" to "Method not allowed"))
        }
    }

    server.createContext("/api/applications") { exchange ->
        if (exchange.requestMethod != "POST") {
            sendResponse(exchange, 405, mapOf("error" to "Method not allowed"))
            return@createContext
        }

        try {
            val body = exchange.requestBody.readAllBytes().decodeToString()
            val req = json.readValue(body, SubmitRequest::class.java)

            val appId = UUID.randomUUID()
            val config = createDefaultConfig(req.productId)

            val workflow = client.newWorkflowStub(
                CreditCheckWorkflow::class.java,
                WorkflowOptions.newBuilder()
                    .setTaskQueue(TASK_QUEUE)
                    .setWorkflowId("credit-check-$appId")
                    .build()
            )

            val workflowReq = CreditCheckRequest(
                appId, req.productId, req.userDetails, config, HTTPBIN_URL, LOS_URL
            )

            WorkflowClient.start(workflow::run, workflowReq)

            sendResponse(exchange, 200, mapOf(
                "applicationId" to appId.toString(),
                "status" to "submitted"
            ))
        } catch (e: Exception) {
            sendResponse(exchange, 500, mapOf("error" to e.message))
        }
    }

    server.executor = Executors.newFixedThreadPool(100)
    server.start()

    println("Temporal worker and HTTP server started successfully on port 8001")
}

private fun sendResponse(exchange: HttpExchange, code: Int, body: Any) {
    val response = json.writeValueAsString(body)
    exchange.responseHeaders["Content-Type"] = "application/json"
    exchange.sendResponseHeaders(code, response.length.toLong())
    exchange.responseBody.use { os ->
        os.write(response.toByteArray())
    }
}

private fun createDefaultConfig(productId: String): LoanProductConfig {
    val enabled = LoanProductConfig.StageConfig(true, 30, 3)
    val thresholds = LoanProductConfig.DecisionThresholds(700, 500)

    return LoanProductConfig(
        productId,
        enabled,
        enabled,
        enabled,
        enabled,
        thresholds,
        Duration.ofMinutes(10)
    )
}
