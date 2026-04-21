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

    @PostMapping("/applications")
    public ResponseEntity<Map<String, Object>> submit(@RequestBody SubmitRequest req) {
        UUID appId = service.submit(req.productId(), req.userDetails(), req.loanAmount());
        return ResponseEntity.ok(Map.of("applicationId", appId.toString(), "status", "submitted"));
    }

    @GetMapping("/applications/{id}")
    public ResponseEntity<LoanApplication> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    record SubmitRequest(String productId, UserDetails userDetails, BigDecimal loanAmount) {}
}
