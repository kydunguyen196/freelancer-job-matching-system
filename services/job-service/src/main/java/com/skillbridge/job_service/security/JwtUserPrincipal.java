package com.skillbridge.job_service.security;

public record JwtUserPrincipal(
        Long userId,
        String email,
        String role
) {
}
