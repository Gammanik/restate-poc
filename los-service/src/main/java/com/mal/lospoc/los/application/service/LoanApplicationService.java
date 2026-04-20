package com.mal.lospoc.los.application.service;

import com.mal.lospoc.common.domain.ApplicationEvent;
import com.mal.lospoc.common.domain.ApplicationState;
import com.mal.lospoc.common.domain.LoanApplication;
import com.mal.lospoc.common.dto.RiskScore;
import com.mal.lospoc.common.dto.UserDetails;
import com.mal.lospoc.los.application.config.ProductConfigLoader;
import com.mal.lospoc.los.application.port.WorkflowOrchestrator;
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
    private final WorkflowOrchestrator orchestrator;

    public LoanApplicationService(
        InMemoryApplicationStore store,
        ProductConfigLoader configLoader,
        WorkflowOrchestrator orchestrator
    ) {
        this.store = store;
        this.configLoader = configLoader;
        this.orchestrator = orchestrator;
    }

    public UUID submitApplication(String productId, UserDetails userDetails, BigDecimal loanAmount) {
        configLoader.getConfig(productId); // Validate product exists

        LoanApplication application = LoanApplication.initiate(productId, userDetails, loanAmount);
        store.save(application);
        store.appendEvent(application.applicationId(), new ApplicationEvent.ApplicationInitiated(productId));

        log.info("Application submitted: {}", application.applicationId());

        orchestrator.startCreditCheck(application.applicationId(), productId);

        return application.applicationId();
    }

    public void applyEvent(UUID applicationId, ApplicationEvent event) {
        LoanApplication app = store.findById(applicationId)
            .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));

        ApplicationState newState = transition(app.state(), event);
        store.save(app.withState(newState));
        store.appendEvent(applicationId, event);

        log.info("Event applied to {}: {} -> {}", applicationId, event.getClass().getSimpleName(), newState.getClass().getSimpleName());
    }

    private ApplicationState transition(ApplicationState current, ApplicationEvent event) {
        return switch (current) {
            case ApplicationState.Initiated i -> switch (event) {
                case ApplicationEvent.UserDetailsCollected e -> new ApplicationState.CollectingUserDetails();
                default -> throw invalidTransition(current, event);
            };
            case ApplicationState.CollectingUserDetails c -> switch (event) {
                case ApplicationEvent.ConsentCaptured e -> new ApplicationState.AwaitingConsent();
                default -> throw invalidTransition(current, event);
            };
            case ApplicationState.AwaitingConsent a -> switch (event) {
                case ApplicationEvent.AecbFetched e -> new ApplicationState.FetchingAecb();
                default -> throw invalidTransition(current, event);
            };
            case ApplicationState.FetchingAecb f -> switch (event) {
                case ApplicationEvent.OpenBankingFetched e -> new ApplicationState.FetchingOpenBanking();
                case ApplicationEvent.DecisionMade e -> new ApplicationState.Decisioning();
                default -> throw invalidTransition(current, event);
            };
            case ApplicationState.FetchingOpenBanking o -> switch (event) {
                case ApplicationEvent.DecisionMade e -> new ApplicationState.Decisioning();
                default -> throw invalidTransition(current, event);
            };
            case ApplicationState.Decisioning d -> switch (event) {
                case ApplicationEvent.DecisionMade e when e.score().outcome() == RiskScore.Outcome.MANUAL ->
                    new ApplicationState.Underwriting(Instant.now(), "pending");
                case ApplicationEvent.DecisionMade e when e.score().outcome() == RiskScore.Outcome.AUTO_APPROVE ->
                    new ApplicationState.Approved(e.score().score(), Instant.now());
                case ApplicationEvent.DecisionMade e when e.score().outcome() == RiskScore.Outcome.AUTO_REJECT ->
                    new ApplicationState.Rejected("Score too low", Instant.now());
                default -> throw invalidTransition(current, event);
            };
            case ApplicationState.Underwriting u -> switch (event) {
                case ApplicationEvent.UnderwritingCompleted e when e.decision().approved() ->
                    new ApplicationState.Approved(0, Instant.now());
                case ApplicationEvent.UnderwritingCompleted e when !e.decision().approved() ->
                    new ApplicationState.Rejected(e.decision().rejectionReason(), Instant.now());
                default -> throw invalidTransition(current, event);
            };
            case ApplicationState.Approved ap -> current;
            case ApplicationState.Rejected r -> current;
            case ApplicationState.Cancelled c -> current;
        };
    }

    private IllegalStateException invalidTransition(ApplicationState state, ApplicationEvent event) {
        return new IllegalStateException("Invalid transition: " + state.getClass().getSimpleName() + " + " + event.getClass().getSimpleName());
    }

    public LoanApplication getApplication(UUID applicationId) {
        return store.findById(applicationId)
            .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
    }
}
