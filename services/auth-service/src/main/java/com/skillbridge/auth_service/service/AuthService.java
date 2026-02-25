package com.skillbridge.auth_service.service;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.auth_service.domain.AuthUser;
import com.skillbridge.auth_service.dto.AuthResponse;
import com.skillbridge.auth_service.dto.LoginRequest;
import com.skillbridge.auth_service.dto.RefreshTokenRequest;
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
    public AuthResponse register(RegisterRequest request) {
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
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        AuthUser user = authUserRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshTokenRequest request) {
        Claims claims;
        try {
            claims = jwtService.parseRefreshToken(request.refreshToken());
        } catch (JwtException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }

        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }

        AuthUser user = authUserRepository.findByEmail(normalizeEmail(subject))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        return issueTokens(user);
    }

    private AuthResponse issueTokens(AuthUser user) {
        return new AuthResponse(
                "Bearer",
                jwtService.generateAccessToken(user),
                jwtService.generateRefreshToken(user),
                jwtService.getAccessTokenExpirationSeconds(),
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
