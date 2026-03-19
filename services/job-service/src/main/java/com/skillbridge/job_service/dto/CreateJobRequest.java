package com.skillbridge.job_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.skillbridge.job_service.domain.EmploymentType;
import com.skillbridge.job_service.domain.JobStatus;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateJobRequest(
        @NotBlank @Size(max = 150) String title,
        @NotBlank @Size(max = 4000) String description,
        @NotNull @DecimalMin(value = "0.01") BigDecimal budgetMin,
        @NotNull @DecimalMin(value = "0.01") BigDecimal budgetMax,
        @Size(max = 20) List<@NotBlank @Size(max = 50) String> tags,
        @Size(max = 255) String companyName,
        @Size(max = 255) String location,
        EmploymentType employmentType,
        Boolean remote,
        @Min(0) Integer experienceYears,
        JobStatus status,
        Instant expiresAt
) {
}
