package com.mal.lospoc.los.api;

import com.mal.lospoc.los.application.service.ApplicationStatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stats")
public class StatsController {
    private final ApplicationStatsService statsService;

    public StatsController(ApplicationStatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    public ResponseEntity<ApplicationStatsService.Stats> getStats() {
        return ResponseEntity.ok(statsService.getStats());
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reset() {
        statsService.reset();
        return ResponseEntity.ok().build();
    }
}
