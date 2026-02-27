package com.skillbridge.notification_service.service;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.notification_service.domain.Notification;
import com.skillbridge.notification_service.domain.NotificationType;
import com.skillbridge.notification_service.dto.NotificationResponse;
import com.skillbridge.notification_service.repository.NotificationRepository;
import com.skillbridge.notification_service.security.JwtUserPrincipal;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications(JwtUserPrincipal principal) {
        Long userId = requireUserId(principal);
        return notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public NotificationResponse markAsRead(Long notificationId, JwtUserPrincipal principal) {
        if (notificationId == null || notificationId < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "notificationId must be greater than 0");
        }
        Long userId = requireUserId(principal);
        Notification notification = notificationRepository.findByIdAndRecipientUserId(notificationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
            notification = notificationRepository.save(notification);
        }

        return toResponse(notification);
    }

    @Transactional
    public void createNotification(Long recipientUserId, NotificationType type, String title, String message) {
        if (recipientUserId == null || recipientUserId < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "recipientUserId must be greater than 0");
        }
        if (type == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type is required");
        }

        Notification notification = new Notification();
        notification.setRecipientUserId(recipientUserId);
        notification.setType(type);
        notification.setTitle(normalizeRequiredText(title, "title"));
        notification.setMessage(normalizeRequiredText(message, "message"));
        notification.setRead(false);
        notificationRepository.save(notification);
    }

    private Long requireUserId(JwtUserPrincipal principal) {
        if (principal == null || principal.userId() == null || principal.userId() < 1) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user in token");
        }
        return principal.userId();
    }

    private String normalizeRequiredText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must not be blank");
        }
        return value.trim();
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getRecipientUserId(),
                notification.getType().name(),
                notification.getTitle(),
                notification.getMessage(),
                notification.isRead(),
                notification.getReadAt(),
                notification.getCreatedAt(),
                notification.getUpdatedAt()
        );
    }
}
