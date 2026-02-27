package com.skillbridge.job_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record JobResponse(
        Long id,
        String title,
        String description,
        BigDecimal budgetMin,
        BigDecimal budgetMax,
        List<String> tags,
        String status,
        Long clientId,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt
) {
}
