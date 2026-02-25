package com.skillbridge.proposal_service.dto;

import java.math.BigDecimal;

public record ProposalCreateResponse(
        String message,
        Long freelancerId,
        String freelancerEmail,
        String role,
        Long jobId,
        BigDecimal price,
        Integer durationDays
) {
}
