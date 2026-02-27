package com.skillbridge.contract_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateMilestoneRequest(
        @NotBlank @Size(max = 255) String title,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull @FutureOrPresent LocalDate dueDate
) {
}
