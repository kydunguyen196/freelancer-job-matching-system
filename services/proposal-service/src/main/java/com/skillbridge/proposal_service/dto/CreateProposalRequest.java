package com.skillbridge.proposal_service.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProposalRequest(
        @NotNull @Min(1) Long jobId,
        @NotBlank @Size(max = 4000) String coverLetter,
        @NotNull @DecimalMin(value = "0.01") BigDecimal price,
        @NotNull @Min(1) Integer durationDays
) {
}
