package com.skillbridge.proposal_service.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProposalResponse(
        Long id,
        Long jobId,
        Long freelancerId,
        String freelancerEmail,
        String coverLetter,
        BigDecimal price,
        Integer durationDays,
        String status,
        Long acceptedByClientId,
        Instant acceptedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
