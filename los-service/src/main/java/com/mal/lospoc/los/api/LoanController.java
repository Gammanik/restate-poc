package com.mal.lospoc.los.api;

import com.mal.lospoc.common.domain.LoanApplication;
import com.mal.lospoc.common.dto.UserDetails;
import com.mal.lospoc.los.application.service.LoanApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class LoanController {
    private final LoanApplicationService service;

    public LoanController(LoanApplicationService service) {
        this.service = service;
    }

    @PostMapping("/credit-check")
    public ResponseEntity<Map<String, Object>> submitApplication(@RequestBody SubmitRequest request) {
        UUID applicationId = service.submitApplication(
            request.productId(),
            request.userDetails(),
            request.loanAmount()
        );
        return ResponseEntity.ok(Map.of(
            "applicationId", applicationId.toString(),
            "status", "submitted"
        ));
    }

    @GetMapping("/applications/{id}")
    public ResponseEntity<LoanApplication> getApplication(@PathVariable("id") UUID applicationId) {
        return ResponseEntity.ok(service.getApplication(applicationId));
    }

    public record SubmitRequest(
        String productId,
        UserDetails userDetails,
        BigDecimal loanAmount
    ) {}
}
