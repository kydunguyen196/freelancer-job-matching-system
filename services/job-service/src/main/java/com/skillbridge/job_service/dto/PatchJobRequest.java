package com.skillbridge.job_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.skillbridge.job_service.domain.EmploymentType;
import com.skillbridge.job_service.domain.JobVisibility;
import com.skillbridge.job_service.domain.WorkMode;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record PatchJobRequest(
        @Size(max = 150) String title,
        @Size(max = 4000) String description,
        @Size(max = 6000) String requirements,
        @Size(max = 6000) String responsibilities,
        @Size(max = 4000) String benefits,
        @DecimalMin(value = "0.01") BigDecimal budgetMin,
        @DecimalMin(value = "0.01") BigDecimal budgetMax,
        @Size(max = 20) List<@Size(max = 50) String> tags,
        @Size(max = 255) String companyName,
        @Size(max = 255) String location,
        EmploymentType employmentType,
        WorkMode workMode,
        Boolean remote,
        @Min(0) Integer experienceYears,
        @Size(max = 120) String category,
        JobVisibility visibility,
        @Min(1) Integer openings,
        Instant expiresAt
) {
}
