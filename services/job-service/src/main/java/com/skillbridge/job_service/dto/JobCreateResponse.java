package com.skillbridge.job_service.dto;

import java.math.BigDecimal;

public record JobCreateResponse(
        String message,
        Long clientId,
        String clientEmail,
        String role,
        String title,
        BigDecimal budgetMin,
        BigDecimal budgetMax
) {
}
