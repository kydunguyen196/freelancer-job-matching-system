package com.skillbridge.job_service.dto;

import java.time.Instant;
import java.util.List;

public record CompanySearchResponse(
        Long clientId,
        String companyName,
        long totalJobs,
        long openJobs,
        Instant latestJobCreatedAt,
        Instant latestJobUpdatedAt,
        List<String> locations,
        List<String> employmentTypes,
        List<String> topTags
) {
}
