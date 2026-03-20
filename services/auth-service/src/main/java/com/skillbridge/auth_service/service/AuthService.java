package com.skillbridge.auth_service.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.auth_service.domain.AuthUser;
import com.skillbridge.auth_service.dto.AuthResponse;
import com.skillbridge.auth_service.dto.LoginRequest;
import com.skillbridge.auth_service.dto.RegisterRequest;
import com.skillbridge.auth_service.repository.AuthUserRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

@Service
public class AuthService {

    private final AuthUserRepository authUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AuthUserRepository authUserRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.authUserRepository = authUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthTokenBundle register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (authUserRepository.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        AuthUser user = new AuthUser();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());

        AuthUser savedUser = authUserRepository.save(user);
        return issueTokens(savedUser);
    }

    @Transactional(readOnly = true)
    public AuthTokenBundle login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        AuthUser user = authUserRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public AuthTokenBundle refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is required");
        }
        Claims claims;
        try {
            claims = jwtService.parseRefreshToken(refreshToken);
        } catch (JwtException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }

        AuthUser user = loadUserBySubject(claims.getSubject(), "Invalid refresh token");
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse getSession(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token is required");
        }

        Claims claims;
        try {
            claims = jwtService.parseAccessToken(accessToken);
        } catch (JwtException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid access token");
        }

        AuthUser user = loadUserBySubject(claims.getSubject(), "Invalid access token");
        long expiresIn = resolveExpiresIn(claims);
        return toSession(user, expiresIn);
    }

    private AuthTokenBundle issueTokens(AuthUser user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        AuthResponse session = toSession(user, jwtService.getAccessTokenExpirationSeconds());
        return new AuthTokenBundle(session, accessToken, refreshToken);
    }

    private AuthResponse toSession(AuthUser user, long expiresInSeconds) {
        return new AuthResponse(
                expiresInSeconds,
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );
    }

    private AuthUser loadUserBySubject(String subject, String invalidTokenMessage) {
        if (subject == null || subject.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, invalidTokenMessage);
        }
        return authUserRepository.findByEmail(normalizeEmail(subject))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private long resolveExpiresIn(Claims claims) {
        if (claims.getExpiration() == null) {
            return 0;
        }
        long seconds = Duration.between(Instant.now(), claims.getExpiration().toInstant()).getSeconds();
        return Math.max(seconds, 0);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
