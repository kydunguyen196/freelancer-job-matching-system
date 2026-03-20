package com.skillbridge.auth_service.service;

import com.skillbridge.auth_service.dto.AuthResponse;

public record AuthTokenBundle(
        AuthResponse session,
        String accessToken,
        String refreshToken
) {
}
