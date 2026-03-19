package com.skillbridge.job_service.dto;

public record JobDashboardResponse(
        long totalJobs,
        long draftJobs,
        long openJobs,
        long inProgressJobs,
        long closedJobs,
        long expiredJobs,
        long totalSavedJobs,
        long totalFollowers
) {
}
