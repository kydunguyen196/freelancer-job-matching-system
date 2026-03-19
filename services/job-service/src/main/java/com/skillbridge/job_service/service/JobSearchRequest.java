package com.skillbridge.job_service.service;

import java.math.BigDecimal;
import java.util.List;

import com.skillbridge.job_service.domain.EmploymentType;
import com.skillbridge.job_service.domain.JobStatus;

public record JobSearchRequest(
        String keyword,
        JobStatus status,
        BigDecimal budgetMin,
        BigDecimal budgetMax,
        Long clientId,
        List<String> tags,
        String location,
        String companyName,
        EmploymentType employmentType,
        Boolean remote,
        Integer experienceYearsMin,
        Integer experienceYearsMax,
        JobSearchSort sort,
        int page,
        int size
) {
}
