package com.skillbridge.job_service.dto;

import java.util.List;

public record RecruiterReportOverviewResponse(
        long totalJobs,
        long openJobs,
        long closedJobs,
        long expiredJobs,
        long jobsCreatedInRange,
        long totalProposals,
        long pendingProposals,
        long reviewingProposals,
        long interviewsScheduled,
        long acceptedProposals,
        long rejectedProposals,
        long totalContracts,
        long activeContracts,
        long completedContracts,
        long cancelledContracts,
        List<String> warnings
) {
}
