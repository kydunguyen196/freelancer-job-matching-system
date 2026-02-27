package com.skillbridge.user_service.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProfileResponse(
        Long userId,
        String email,
        String role,
        List<String> skills,
        BigDecimal hourlyRate,
        String overview,
        String companyName
) {
}
