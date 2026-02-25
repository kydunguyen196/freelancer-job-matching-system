package com.skillbridge.job_service.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateJobRequest(
        @NotBlank @Size(max = 150) String title,
        @NotBlank @Size(max = 4000) String description,
        @NotNull @DecimalMin(value = "0.01") BigDecimal budgetMin,
        @NotNull @DecimalMin(value = "0.01") BigDecimal budgetMax
) {
}
