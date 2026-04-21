package com.mal.lospoc.los.application.service;

import com.mal.lospoc.common.domain.ApplicationEvent;
import com.mal.lospoc.common.domain.ApplicationState;
import com.mal.lospoc.common.domain.LoanApplication;
import com.mal.lospoc.common.dto.UserDetails;
import com.mal.lospoc.los.application.config.ProductConfigLoader;
import com.mal.lospoc.los.infrastructure.InMemoryApplicationStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class LoanApplicationService {
    private static final Logger log = LoggerFactory.getLogger(LoanApplicationService.class);

    private final InMemoryApplicationStore store;
    private final ProductConfigLoader configLoader;

    public LoanApplicationService(InMemoryApplicationStore store, ProductConfigLoader configLoader) {
        this.store = store;
        this.configLoader = configLoader;
    }

    public UUID submit(String productId, UserDetails userDetails, BigDecimal loanAmount) {
        configLoader.getConfig(productId); // Validate

        LoanApplication app = LoanApplication.initiate(productId, userDetails, loanAmount);
        store.save(app);
        store.appendEvent(app.applicationId(), new ApplicationEvent.Started(productId));

        log.info("Application submitted: {}", app.applicationId());
        return app.applicationId();
    }

    public void applyEvent(UUID appId, ApplicationEvent event) {
        LoanApplication app = store.findById(appId)
            .orElseThrow(() -> new IllegalArgumentException("App not found: " + appId));

        ApplicationState newState = switch (app.state()) {
            case ApplicationState.Submitted s -> switch (event) {
                case ApplicationEvent.Started e -> new ApplicationState.Processing("consent");
                default -> throw invalid(app.state(), event);
            };
            case ApplicationState.Processing p -> switch (event) {
                case ApplicationEvent.StageCompleted e -> new ApplicationState.Processing(nextStage(e.stage()));
                case ApplicationEvent.DecisionMade e when e.score().outcome() == com.mal.lospoc.common.dto.RiskScore.Outcome.MANUAL ->
                    new ApplicationState.ManualReview(Instant.now());
                case ApplicationEvent.DecisionMade e when e.score().outcome() == com.mal.lospoc.common.dto.RiskScore.Outcome.AUTO_APPROVE ->
                    new ApplicationState.Approved(e.score().score(), Instant.now());
                case ApplicationEvent.DecisionMade e ->
                    new ApplicationState.Rejected("Score too low", Instant.now());
                default -> throw invalid(app.state(), event);
            };
            case ApplicationState.ManualReview m -> switch (event) {
                case ApplicationEvent.Completed e when e.approved() ->
                    new ApplicationState.Approved(0, Instant.now());
                case ApplicationEvent.Completed e ->
                    new ApplicationState.Rejected(e.reason(), Instant.now());
                default -> throw invalid(app.state(), event);
            };
            case ApplicationState.Approved a -> app.state();
            case ApplicationState.Rejected r -> app.state();
        };

        store.save(app.withState(newState));
        store.appendEvent(appId, event);

        log.info("Event applied: {} -> {}", event.getClass().getSimpleName(), newState.getClass().getSimpleName());
    }

    private String nextStage(String current) {
        return switch (current) {
            case "consent" -> "aecb";
            case "aecb" -> "open_banking";
            case "open_banking" -> "decisioning";
            default -> "decisioning";
        };
    }

    private IllegalStateException invalid(ApplicationState state, ApplicationEvent event) {
        return new IllegalStateException("Invalid: " + state.getClass().getSimpleName() + " + " + event.getClass().getSimpleName());
    }

    public LoanApplication get(UUID appId) {
        return store.findById(appId).orElseThrow(() -> new IllegalArgumentException("Not found: " + appId));
    }
}
