package com.skillbridge.job_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record JobResponse(
        Long id,
        String title,
        String description,
        String requirements,
        String responsibilities,
        String benefits,
        BigDecimal budgetMin,
        BigDecimal budgetMax,
        List<String> tags,
        String status,
        Long clientId,
        String companyName,
        String location,
        String employmentType,
        String workMode,
        boolean remote,
        Integer experienceYears,
        String category,
        String visibility,
        Integer openings,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt,
        Instant expiresAt,
        Instant closedAt,
        boolean savedByCurrentUser,
        boolean companyFollowedByCurrentUser
) {
}
