package com.skillbridge.notification_service.security;

public record JwtUserPrincipal(
        Long userId,
        String email,
        String role
) {
}
