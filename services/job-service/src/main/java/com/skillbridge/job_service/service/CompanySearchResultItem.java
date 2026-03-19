package com.skillbridge.job_service.service;

import java.time.Instant;
import java.util.List;

public record CompanySearchResultItem(
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
