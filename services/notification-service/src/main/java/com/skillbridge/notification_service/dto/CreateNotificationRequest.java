package com.skillbridge.notification_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateNotificationRequest(
        @NotNull @Min(1) Long recipientUserId,
        @NotBlank String type,
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 4000) String message
) {
}
