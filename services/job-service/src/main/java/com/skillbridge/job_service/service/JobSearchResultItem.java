package com.skillbridge.job_service.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.skillbridge.job_service.domain.Job;

public record JobSearchResultItem(
        Long id,
        String title,
        String description,
        BigDecimal budgetMin,
        BigDecimal budgetMax,
        List<String> tags,
        String status,
        Long clientId,
        String companyName,
        String location,
        String employmentType,
        boolean remote,
        Integer experienceYears,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt,
        Instant expiresAt,
        Instant closedAt
) {

    public static JobSearchResultItem fromJob(Job job) {
        return new JobSearchResultItem(
                job.getId(),
                job.getTitle(),
                job.getDescription(),
                job.getBudgetMin(),
                job.getBudgetMax(),
                List.copyOf(job.getTags()),
                job.getStatus().name(),
                job.getClientId(),
                job.getCompanyName(),
                job.getLocation(),
                job.getEmploymentType().name(),
                job.isRemote(),
                job.getExperienceYears(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getPublishedAt(),
                job.getExpiresAt(),
                job.getClosedAt()
        );
    }
}
