package com.mal.lospoc.common.dto;

public record EmploymentRecord(
    String recordId,
    String employmentStatus,
    String employmentType,
    int tenureMonths,
    int monthlySalary
) {}
