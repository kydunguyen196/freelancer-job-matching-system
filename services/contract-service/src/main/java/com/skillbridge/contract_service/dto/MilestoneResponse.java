package com.skillbridge.contract_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record MilestoneResponse(
        Long id,
        Long contractId,
        String title,
        BigDecimal amount,
        LocalDate dueDate,
        String status,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
