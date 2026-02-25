package com.skillbridge.proposal_service.security;

public record JwtUserPrincipal(
        Long userId,
        String email,
        String role
) {
}
