package com.skillbridge.auth_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.auth_service.domain.AuthUser;
import com.skillbridge.auth_service.domain.UserRole;
import com.skillbridge.auth_service.dto.AuthResponse;
import com.skillbridge.auth_service.dto.LoginRequest;
import com.skillbridge.auth_service.dto.RefreshTokenRequest;
import com.skillbridge.auth_service.dto.RegisterRequest;
import com.skillbridge.auth_service.repository.AuthUserRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthUserRepository authUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerShouldNormalizeEmailHashPasswordAndIssueTokens() {
        RegisterRequest request = new RegisterRequest("  Client@Example.com ", "password123", UserRole.CLIENT);
        when(authUserRepository.existsByEmail("client@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(authUserRepository.save(any(AuthUser.class))).thenAnswer(invocation -> {
            AuthUser user = invocation.getArgument(0);
            user.setId(101L);
            return user;
        });

        mockTokenIssuance(101L, "client@example.com", UserRole.CLIENT);

        AuthResponse response = authService.register(request);

        ArgumentCaptor<AuthUser> userCaptor = ArgumentCaptor.forClass(AuthUser.class);
        verify(authUserRepository).save(userCaptor.capture());
        AuthUser savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("client@example.com");
        assertThat(savedUser.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(savedUser.getRole()).isEqualTo(UserRole.CLIENT);

        assertThat(response.userId()).isEqualTo(101L);
        assertThat(response.email()).isEqualTo("client@example.com");
        assertThat(response.role()).isEqualTo("CLIENT");
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.expiresIn()).isEqualTo(900L);
    }

    @Test
    void registerShouldRejectDuplicateEmail() {
        RegisterRequest request = new RegisterRequest("client@example.com", "password123", UserRole.CLIENT);
        when(authUserRepository.existsByEmail("client@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void loginShouldRejectInvalidPassword() {
        AuthUser existingUser = new AuthUser();
        existingUser.setId(88L);
        existingUser.setEmail("freelancer@example.com");
        existingUser.setPasswordHash("hashed");
        existingUser.setRole(UserRole.FREELANCER);
        when(authUserRepository.findByEmail("freelancer@example.com")).thenReturn(java.util.Optional.of(existingUser));
        when(passwordEncoder.matches("wrong-password", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("freelancer@example.com", "wrong-password")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void refreshShouldRejectInvalidRefreshToken() {
        when(jwtService.parseRefreshToken("bad-token")).thenThrow(new JwtException("invalid"));

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("bad-token")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void refreshShouldIssueNewTokensWhenRefreshTokenIsValid() {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(claims.getSubject()).thenReturn("client@example.com");
        when(jwtService.parseRefreshToken("refresh-token")).thenReturn(claims);

        AuthUser user = new AuthUser();
        user.setId(777L);
        user.setEmail("client@example.com");
        user.setRole(UserRole.CLIENT);
        user.setPasswordHash("hashed");
        when(authUserRepository.findByEmail("client@example.com")).thenReturn(java.util.Optional.of(user));

        mockTokenIssuance(777L, "client@example.com", UserRole.CLIENT);

        AuthResponse response = authService.refresh(new RefreshTokenRequest("refresh-token"));

        assertThat(response.userId()).isEqualTo(777L);
        assertThat(response.email()).isEqualTo("client@example.com");
        assertThat(response.role()).isEqualTo("CLIENT");
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
    }

    private void mockTokenIssuance(Long userId, String email, UserRole role) {
        when(jwtService.generateAccessToken(any(AuthUser.class))).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any(AuthUser.class))).thenReturn("refresh-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
    }
}
