package com.mal.lospoc.los.api;

import com.mal.lospoc.common.domain.ApplicationEvent;
import com.mal.lospoc.los.application.service.LoanApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/applications")
public class InternalController {
    private final LoanApplicationService service;

    public InternalController(LoanApplicationService service) {
        this.service = service;
    }

    @PostMapping("/{id}/events")
    public ResponseEntity<Map<String, String>> applyEvent(
        @PathVariable("id") UUID applicationId,
        @RequestBody ApplicationEvent event
    ) {
        service.applyEvent(applicationId, event);
        return ResponseEntity.ok(Map.of("status", "event_applied"));
    }
}
