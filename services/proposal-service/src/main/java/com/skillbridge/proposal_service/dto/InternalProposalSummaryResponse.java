package com.skillbridge.proposal_service.dto;

public record InternalProposalSummaryResponse(
        long totalProposals,
        long pendingProposals,
        long reviewingProposals,
        long interviewsScheduled,
        long acceptedProposals,
        long rejectedProposals,
        long hiresEstimated
) {
}
