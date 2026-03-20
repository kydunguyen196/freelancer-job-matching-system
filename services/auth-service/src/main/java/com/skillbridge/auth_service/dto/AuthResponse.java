package com.skillbridge.auth_service.dto;

public record AuthResponse(
        long expiresIn,
        Long userId,
        String email,
        String role
) {
}
