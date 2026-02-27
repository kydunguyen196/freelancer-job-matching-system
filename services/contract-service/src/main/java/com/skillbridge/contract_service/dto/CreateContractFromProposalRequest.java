package com.skillbridge.contract_service.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateContractFromProposalRequest(
        @NotNull @Min(1) Long proposalId,
        @NotNull @Min(1) Long jobId,
        @NotNull @Min(1) Long clientId,
        @NotNull @Min(1) Long freelancerId,
        @NotNull @DecimalMin("0.01") BigDecimal milestoneAmount,
        @NotNull @Min(1) Integer durationDays
) {
}
