package com.skillbridge.auth_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.skillbridge.auth_service.dto.AuthResponse;
import com.skillbridge.auth_service.dto.LoginRequest;
import com.skillbridge.auth_service.dto.RefreshTokenRequest;
import com.skillbridge.auth_service.dto.RegisterRequest;
import com.skillbridge.auth_service.service.AuthCookieService;
import com.skillbridge.auth_service.service.AuthService;
import com.skillbridge.auth_service.service.AuthTokenBundle;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Validated
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService authCookieService;

    public AuthController(AuthService authService, AuthCookieService authCookieService) {
        this.authService = authService;
        this.authCookieService = authCookieService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response
    ) {
        AuthTokenBundle issued = authService.register(request);
        authCookieService.writeAuthCookies(response, issued);
        return ResponseEntity.status(HttpStatus.CREATED).body(issued.session());
    }

    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        AuthTokenBundle issued = authService.login(request);
        authCookieService.writeAuthCookies(response, issued);
        return issued.session();
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse response
    ) {
        String refreshToken = authCookieService.resolveRefreshToken(
                httpServletRequest,
                request != null ? request.refreshToken() : null
        );
        AuthTokenBundle issued = authService.refresh(refreshToken);
        authCookieService.writeAuthCookies(response, issued);
        return issued.session();
    }

    @GetMapping("/session")
    public AuthResponse session(HttpServletRequest request) {
        String accessToken = authCookieService.resolveAccessToken(request);
        return authService.getSession(accessToken);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        authCookieService.clearAuthCookies(response);
        return ResponseEntity.noContent().build();
    }
}
