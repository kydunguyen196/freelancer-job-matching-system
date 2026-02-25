package com.skillbridge.auth_service.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.skillbridge.auth_service.config.JwtProperties;
import com.skillbridge.auth_service.domain.AuthUser;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    public static final String ACCESS_TOKEN_TYPE = "access";
    public static final String REFRESH_TOKEN_TYPE = "refresh";

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes for HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(AuthUser user) {
        return generateToken(user, ACCESS_TOKEN_TYPE, jwtProperties.getAccessTokenExpirationMs());
    }

    public String generateRefreshToken(AuthUser user) {
        return generateToken(user, REFRESH_TOKEN_TYPE, jwtProperties.getRefreshTokenExpirationMs());
    }

    public Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new JwtException("Invalid JWT token", ex);
        }
    }

    public Claims parseRefreshToken(String token) {
        Claims claims = parseClaims(token);
        String tokenType = claims.get("type", String.class);
        if (!REFRESH_TOKEN_TYPE.equals(tokenType)) {
            throw new JwtException("Invalid token type");
        }
        return claims;
    }

    public long getAccessTokenExpirationSeconds() {
        return jwtProperties.getAccessTokenExpirationMs() / 1000;
    }

    private String generateToken(AuthUser user, String tokenType, long ttlMs) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(ttlMs)))
                .claim("type", tokenType)
                .claim("userId", user.getId())
                .claim("role", user.getRole().name())
                .signWith(signingKey)
                .compact();
    }
}
