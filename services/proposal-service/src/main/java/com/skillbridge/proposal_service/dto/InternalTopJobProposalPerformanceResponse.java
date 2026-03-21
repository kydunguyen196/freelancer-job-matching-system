package com.skillbridge.proposal_service.dto;

public record InternalTopJobProposalPerformanceResponse(
        Long jobId,
        long totalProposals,
        long pendingProposals,
        long reviewingProposals,
        long interviewsScheduled,
        long acceptedProposals,
        long rejectedProposals,
        double acceptanceRate
) {
}
