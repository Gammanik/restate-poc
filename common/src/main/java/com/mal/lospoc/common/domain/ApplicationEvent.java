package com.mal.lospoc.common.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.mal.lospoc.common.dto.*;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ApplicationEvent.ApplicationInitiated.class, name = "ApplicationInitiated"),
    @JsonSubTypes.Type(value = ApplicationEvent.UserDetailsCollected.class, name = "UserDetailsCollected"),
    @JsonSubTypes.Type(value = ApplicationEvent.ConsentCaptured.class, name = "ConsentCaptured"),
    @JsonSubTypes.Type(value = ApplicationEvent.AecbFetched.class, name = "AecbFetched"),
    @JsonSubTypes.Type(value = ApplicationEvent.OpenBankingFetched.class, name = "OpenBankingFetched"),
    @JsonSubTypes.Type(value = ApplicationEvent.DecisionMade.class, name = "DecisionMade"),
    @JsonSubTypes.Type(value = ApplicationEvent.UnderwritingStarted.class, name = "UnderwritingStarted"),
    @JsonSubTypes.Type(value = ApplicationEvent.UnderwritingCompleted.class, name = "UnderwritingCompleted"),
    @JsonSubTypes.Type(value = ApplicationEvent.ApplicationCancelled.class, name = "ApplicationCancelled")
})
public sealed interface ApplicationEvent {
    record ApplicationInitiated(String productId) implements ApplicationEvent {}
    record UserDetailsCollected(UserDetails details) implements ApplicationEvent {}
    record ConsentCaptured(ConsentRecord consent) implements ApplicationEvent {}
    record AecbFetched(AecbReport report) implements ApplicationEvent {}
    record OpenBankingFetched(OpenBankingSnapshot snapshot) implements ApplicationEvent {}
    record DecisionMade(RiskScore score) implements ApplicationEvent {}
    record UnderwritingStarted(String assignedTo) implements ApplicationEvent {}
    record UnderwritingCompleted(UnderwritingDecision decision) implements ApplicationEvent {}
    record ApplicationCancelled(String reason) implements ApplicationEvent {}
}
