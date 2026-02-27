package com.skillbridge.notification_service.dto;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        Long recipientUserId,
        String type,
        String title,
        String message,
        boolean read,
        Instant readAt,
        Instant createdAt,
        Instant updatedAt
) {
}
