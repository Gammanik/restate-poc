package com.mal.lospoc.common.domain;

import java.time.Instant;

public sealed interface ApplicationState {
    record Submitted() implements ApplicationState {}
    record Processing(String stage) implements ApplicationState {}
    record ManualReview(Instant since) implements ApplicationState {}
    record Approved(int score, Instant at) implements ApplicationState {}
    record Rejected(String reason, Instant at) implements ApplicationState {}
}
