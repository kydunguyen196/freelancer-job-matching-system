package com.skillbridge.notification_service.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.notification_service.dto.CreateNotificationRequest;
import com.skillbridge.notification_service.dto.NotificationResponse;
import com.skillbridge.notification_service.security.JwtUserPrincipal;
import com.skillbridge.notification_service.service.NotificationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

@Validated
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final NotificationService notificationService;
    private final String internalApiKey;

    public NotificationController(
            NotificationService notificationService,
            @Value("${app.internal.api-key}") String internalApiKey
    ) {
        this.notificationService = notificationService;
        this.internalApiKey = internalApiKey;
    }

    @GetMapping("/me")
    public List<NotificationResponse> getMyNotifications(Authentication authentication) {
        return notificationService.getMyNotifications(extractPrincipal(authentication));
    }

    @PatchMapping("/{notificationId}/read")
    public NotificationResponse markAsRead(
            @PathVariable @Min(1) Long notificationId,
            Authentication authentication
    ) {
        return notificationService.markAsRead(notificationId, extractPrincipal(authentication));
    }

    @PostMapping("/internal")
    @ResponseStatus(HttpStatus.CREATED)
    public void createInternalNotification(
            @Valid @RequestBody CreateNotificationRequest request,
            @RequestHeader(name = INTERNAL_API_KEY_HEADER, required = false) String providedApiKey
    ) {
        requireInternalApiKey(providedApiKey);
        notificationService.createNotificationByType(
                request.recipientUserId(),
                request.type(),
                request.title(),
                request.message(),
                request.recipientEmail()
        );
    }

    private void requireInternalApiKey(String providedApiKey) {
        if (providedApiKey == null || !providedApiKey.equals(internalApiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal API key");
        }
    }

    private JwtUserPrincipal extractPrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtUserPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal;
    }
}
