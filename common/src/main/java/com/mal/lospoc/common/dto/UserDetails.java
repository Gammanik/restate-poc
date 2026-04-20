package com.mal.lospoc.common.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UserDetails(
    String emiratesId,
    String name,
    LocalDate dateOfBirth,
    String address,
    BigDecimal incomeClaimed
) {}
