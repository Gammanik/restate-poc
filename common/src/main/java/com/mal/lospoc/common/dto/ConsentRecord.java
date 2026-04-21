package com.mal.lospoc.common.dto;

import java.time.Instant;
import java.util.List;

public record ConsentRecord(
    String consentRecordId,
    List<String> consentTypes,
    Instant signedAt,
    Instant validUntil
) {}
