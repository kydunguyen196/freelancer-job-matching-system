package com.skillbridge.proposal_service.dto;

import java.util.List;

public record ProposalDashboardResponse(
        long totalApplications,
        long pendingApplications,
        long reviewingApplications,
        long interviewsScheduled,
        long acceptedApplications,
        long rejectedApplications,
        List<JobProposalStatsResponse> jobStats
) {
}
