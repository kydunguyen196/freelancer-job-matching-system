package com.skillbridge.auth_service.service;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.skillbridge.auth_service.config.AuthCookieProperties;
import com.skillbridge.auth_service.config.JwtProperties;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class AuthCookieService {

    private final AuthCookieProperties cookieProperties;
    private final JwtProperties jwtProperties;

    public AuthCookieService(AuthCookieProperties cookieProperties, JwtProperties jwtProperties) {
        this.cookieProperties = cookieProperties;
        this.jwtProperties = jwtProperties;
    }

    public void writeAuthCookies(HttpServletResponse response, AuthTokenBundle bundle) {
        addCookie(
                response,
                cookieProperties.getAccessName(),
                bundle.accessToken(),
                Duration.ofMillis(jwtProperties.getAccessTokenExpirationMs()),
                cookieProperties.getAccessPath()
        );
        addCookie(
                response,
                cookieProperties.getRefreshName(),
                bundle.refreshToken(),
                Duration.ofMillis(jwtProperties.getRefreshTokenExpirationMs()),
                cookieProperties.getRefreshPath()
        );
    }

    public void clearAuthCookies(HttpServletResponse response) {
        addCookie(response, cookieProperties.getAccessName(), "", Duration.ZERO, cookieProperties.getAccessPath());
        addCookie(response, cookieProperties.getRefreshName(), "", Duration.ZERO, cookieProperties.getRefreshPath());
    }

    public String resolveAccessToken(HttpServletRequest request) {
        return firstNonBlank(readCookie(request, cookieProperties.getAccessName()), null);
    }

    public String resolveRefreshToken(HttpServletRequest request, String requestToken) {
        return firstNonBlank(requestToken, readCookie(request, cookieProperties.getRefreshName()));
    }

    private String readCookie(HttpServletRequest request, String cookieName) {
        if (request == null || request.getCookies() == null || !StringUtils.hasText(cookieName)) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                return cookie.getValue().trim();
            }
        }
        return null;
    }

    private void addCookie(HttpServletResponse response, String name, String value, Duration maxAge, String path) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .sameSite(cookieProperties.getSameSite())
                .path(StringUtils.hasText(path) ? path : "/")
                .maxAge(maxAge);
        if (StringUtils.hasText(cookieProperties.getDomain())) {
            builder.domain(cookieProperties.getDomain().trim());
        }
        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        if (StringUtils.hasText(second)) {
            return second.trim();
        }
        return null;
    }
}
