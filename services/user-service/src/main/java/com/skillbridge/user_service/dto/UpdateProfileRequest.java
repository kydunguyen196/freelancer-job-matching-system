package com.skillbridge.user_service.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        List<@NotBlank @Size(max = 80) String> skills,
        @DecimalMin(value = "0.01") BigDecimal hourlyRate,
        @Size(max = 4000) String overview,
        @Size(max = 255) String companyName
) {
}
