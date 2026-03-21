package com.skillbridge.job_service.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RecruiterTopJobPerformanceResponse(
        Long jobId,
        String title,
        String status,
        BigDecimal budgetMin,
        BigDecimal budgetMax,
        Instant createdAt,
        long totalProposals,
        long pendingProposals,
        long reviewingProposals,
        long interviewsScheduled,
        long acceptedProposals,
        long rejectedProposals,
        double acceptanceRate
) {
}
