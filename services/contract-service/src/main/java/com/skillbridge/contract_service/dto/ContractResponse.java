package com.skillbridge.contract_service.dto;

import java.time.Instant;
import java.util.List;

public record ContractResponse(
        Long id,
        Long sourceProposalId,
        Long jobId,
        Long clientId,
        Long freelancerId,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        List<MilestoneResponse> milestones
) {
}
