package com.skillbridge.user_service.security;

public record JwtUserPrincipal(
        Long userId,
        String email,
        String role
) {
}
