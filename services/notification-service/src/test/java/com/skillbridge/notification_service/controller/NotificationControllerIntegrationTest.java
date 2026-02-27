package com.skillbridge.notification_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.skillbridge.notification_service.config.JwtProperties;
import com.skillbridge.notification_service.config.SecurityConfig;
import com.skillbridge.notification_service.dto.NotificationResponse;
import com.skillbridge.notification_service.security.JwtAuthenticationFilter;
import com.skillbridge.notification_service.service.NotificationService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@WebMvcTest(controllers = NotificationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "app.jwt.secret=abcdefghijklmnopqrstuvwxyz123456",
        "app.jwt.access-token-expiration-ms=900000",
        "app.jwt.refresh-token-expiration-ms=604800000"
})
class NotificationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void getMyNotificationsShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/notifications/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyNotificationsShouldReturnDataForAuthenticatedUser() throws Exception {
        when(notificationService.getMyNotifications(any())).thenReturn(List.of(new NotificationResponse(
                1L,
                50L,
                "PROPOSAL_CREATED",
                "New proposal received",
                "A freelancer applied to your job #123",
                false,
                null,
                Instant.now(),
                Instant.now()
        )));

        mockMvc.perform(get("/notifications/me")
                        .header("Authorization", "Bearer " + tokenFor(50L, "CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].type").value("PROPOSAL_CREATED"));
    }

    @Test
    void markAsReadShouldRequireAuthentication() throws Exception {
        mockMvc.perform(patch("/notifications/7/read"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void markAsReadShouldValidatePathVariable() throws Exception {
        mockMvc.perform(patch("/notifications/0/read")
                        .header("Authorization", "Bearer " + tokenFor(50L, "CLIENT")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void markAsReadShouldReturnUpdatedNotification() throws Exception {
        when(notificationService.markAsRead(any(), any())).thenReturn(new NotificationResponse(
                9L,
                50L,
                "PROPOSAL_ACCEPTED",
                "Proposal accepted",
                "Your proposal for job #123 has been accepted",
                true,
                Instant.now(),
                Instant.now().minusSeconds(60),
                Instant.now()
        ));

        mockMvc.perform(patch("/notifications/9/read")
                        .header("Authorization", "Bearer " + tokenFor(50L, "FREELANCER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.read").value(true));
    }

    private String tokenFor(Long userId, String role) {
        SecretKey key = Keys.hmacShaKeyFor("abcdefghijklmnopqrstuvwxyz123456".getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("user@example.com")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .claim("userId", userId)
                .claim("role", role)
                .signWith(key)
                .compact();
    }
}
