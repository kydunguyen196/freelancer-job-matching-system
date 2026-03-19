package com.skillbridge.job_service.dto;

import java.time.Instant;

public record FollowedCompanyResponse(
        Long clientId,
        String companyName,
        Instant followedAt
) {
}
