package com.skillbridge.proposal_service.dto;

public record JobProposalStatsResponse(
        Long jobId,
        long totalApplications,
        long pendingApplications,
        long reviewingApplications,
        long interviewsScheduled,
        long acceptedApplications,
        long rejectedApplications
) {
}
