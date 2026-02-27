package com.skillbridge.contract_service.security;

public record JwtUserPrincipal(
        Long userId,
        String email,
        String role
) {
}
