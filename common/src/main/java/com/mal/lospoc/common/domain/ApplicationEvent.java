package com.mal.lospoc.common.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.mal.lospoc.common.dto.RiskScore;
import com.mal.lospoc.common.dto.UnderwritingDecision;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ApplicationEvent.Started.class, name = "Started"),
    @JsonSubTypes.Type(value = ApplicationEvent.StageCompleted.class, name = "StageCompleted"),
    @JsonSubTypes.Type(value = ApplicationEvent.DecisionMade.class, name = "DecisionMade"),
    @JsonSubTypes.Type(value = ApplicationEvent.Completed.class, name = "Completed")
})
public sealed interface ApplicationEvent {
    record Started(String productId) implements ApplicationEvent {}
    record StageCompleted(String stage, Object data) implements ApplicationEvent {}
    record DecisionMade(RiskScore score) implements ApplicationEvent {}
    record Completed(boolean approved, String reason) implements ApplicationEvent {}
}
