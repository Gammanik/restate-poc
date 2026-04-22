package com.mal.lospoc.restate.controller

import com.mal.lospoc.common.domain.LoanProductConfig
import com.mal.lospoc.common.dto.UserDetails
import com.mal.lospoc.restate.service.LoanApplicationService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

data class SubmitRequest(
    val productId: String,
    val userDetails: UserDetails,
    val loanAmount: BigDecimal
)

data class ApplicationResponse(
    val applicationId: String,
    val status: String,
    val approvedAmount: String? = null,
    val decisionReason: String? = null,
    val error: String? = null,
    val message: String? = null
)

@RestController
class ApplicationController(
    private val loanApplicationService: LoanApplicationService
) {

    @GetMapping("/")
    fun healthCheck(): Map<String, String> {
        return mapOf("status" to "ok", "service" to "restate")
    }

    @PostMapping("/api/applications")
    fun submitApplication(@RequestBody request: SubmitRequest): ResponseEntity<ApplicationResponse> {
        return try {
            val result = loanApplicationService.submitApplication(request)

            when (result.status) {
                "approved" -> ResponseEntity.ok(
                    ApplicationResponse(
                        applicationId = result.applicationId.toString(),
                        status = "approved",
                        approvedAmount = result.approvedAmount.toString(),
                        decisionReason = result.decisionReason
                    )
                )
                "rejected" -> ResponseEntity.ok(
                    ApplicationResponse(
                        applicationId = result.applicationId.toString(),
                        status = "rejected",
                        decisionReason = result.decisionReason
                    )
                )
                "timeout" -> ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(
                    ApplicationResponse(
                        applicationId = result.applicationId.toString(),
                        status = "timeout",
                        error = "Gateway Timeout",
                        message = result.decisionReason
                    )
                )
                else -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApplicationResponse(
                        applicationId = result.applicationId.toString(),
                        status = "failed",
                        error = "Internal Server Error",
                        message = result.decisionReason
                    )
                )
            }
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApplicationResponse(
                    applicationId = "unknown",
                    status = "error",
                    error = e.message ?: "Unknown error"
                )
            )
        }
    }
}
