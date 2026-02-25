package com.skillbridge.auth_service.dto;

public record AuthResponse(
        String tokenType,
        String accessToken,
        String refreshToken,
        long expiresIn,
        Long userId,
        String email,
        String role
) {
}
