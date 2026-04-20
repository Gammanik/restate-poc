package com.mal.lospoc.los.api;

import com.mal.lospoc.common.dto.UnderwritingDecision;
import com.mal.lospoc.los.application.port.WorkflowOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/underwriting")
public class UnderwritingController {
    private final WorkflowOrchestrator orchestrator;

    public UnderwritingController(WorkflowOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/{id}/decision")
    public ResponseEntity<Map<String, String>> submitDecision(
        @PathVariable("id") UUID applicationId,
        @RequestBody UnderwritingDecision decision
    ) {
        orchestrator.signalUnderwriterDecision(applicationId, decision);
        return ResponseEntity.ok(Map.of("status", "decision_submitted"));
    }
}
