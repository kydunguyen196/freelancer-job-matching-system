package com.skillbridge.job_service.dto;

import com.skillbridge.job_service.domain.JobStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateJobStatusRequest(
        @NotNull JobStatus status
) {
}
