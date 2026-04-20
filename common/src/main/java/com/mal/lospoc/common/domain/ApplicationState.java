package com.mal.lospoc.common.domain;

import java.time.Instant;

public sealed interface ApplicationState {
    record Initiated() implements ApplicationState {}
    record CollectingUserDetails() implements ApplicationState {}
    record AwaitingConsent() implements ApplicationState {}
    record FetchingAecb() implements ApplicationState {}
    record FetchingOpenBanking() implements ApplicationState {}
    record Decisioning() implements ApplicationState {}
    record Underwriting(Instant since, String assignedTo) implements ApplicationState {}
    record Approved(int score, Instant at) implements ApplicationState {}
    record Rejected(String reason, Instant at) implements ApplicationState {}
    record Cancelled(String reason) implements ApplicationState {}
}
