package com.skillbridge.notification_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.notification_service.domain.Notification;
import com.skillbridge.notification_service.domain.NotificationType;
import com.skillbridge.notification_service.dto.NotificationResponse;
import com.skillbridge.notification_service.repository.NotificationRepository;
import com.skillbridge.notification_service.security.JwtUserPrincipal;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void getMyNotificationsShouldRequireValidUser() {
        JwtUserPrincipal invalidPrincipal = new JwtUserPrincipal(null, "user@example.com", "CLIENT");

        assertThatThrownBy(() -> notificationService.getMyNotifications(invalidPrincipal))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void createNotificationShouldValidateInput() {
        assertThatThrownBy(() -> notificationService.createNotification(0L, NotificationType.PROPOSAL_CREATED, "t", "m"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> notificationService.createNotification(10L, null, "t", "m"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> notificationService.createNotification(10L, NotificationType.PROPOSAL_ACCEPTED, "  ", "m"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createNotificationShouldPersistNormalizedFields() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(11L);
            return notification;
        });

        notificationService.createNotification(99L, NotificationType.PROPOSAL_CREATED, " New Proposal ", " Proposal received ");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getRecipientUserId()).isEqualTo(99L);
        assertThat(saved.getType()).isEqualTo(NotificationType.PROPOSAL_CREATED);
        assertThat(saved.getTitle()).isEqualTo("New Proposal");
        assertThat(saved.getMessage()).isEqualTo("Proposal received");
        assertThat(saved.isRead()).isFalse();
    }

    @Test
    void markAsReadShouldMarkUnreadNotification() {
        Notification unread = new Notification();
        unread.setId(7L);
        unread.setRecipientUserId(101L);
        unread.setType(NotificationType.PROPOSAL_ACCEPTED);
        unread.setTitle("Proposal accepted");
        unread.setMessage("Your proposal has been accepted");
        unread.setRead(false);

        when(notificationRepository.findByIdAndRecipientUserId(7L, 101L)).thenReturn(Optional.of(unread));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationResponse response = notificationService.markAsRead(7L, new JwtUserPrincipal(101L, "f@example.com", "FREELANCER"));

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.read()).isTrue();
        assertThat(response.readAt()).isNotNull();
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void markAsReadShouldBeIdempotentForAlreadyReadNotification() {
        Notification alreadyRead = new Notification();
        alreadyRead.setId(8L);
        alreadyRead.setRecipientUserId(101L);
        alreadyRead.setType(NotificationType.PROPOSAL_ACCEPTED);
        alreadyRead.setTitle("Proposal accepted");
        alreadyRead.setMessage("Your proposal has been accepted");
        alreadyRead.setRead(true);
        alreadyRead.setReadAt(Instant.now().minusSeconds(60));

        when(notificationRepository.findByIdAndRecipientUserId(8L, 101L)).thenReturn(Optional.of(alreadyRead));

        NotificationResponse response = notificationService.markAsRead(8L, new JwtUserPrincipal(101L, "f@example.com", "FREELANCER"));

        assertThat(response.id()).isEqualTo(8L);
        assertThat(response.read()).isTrue();
        assertThat(response.readAt()).isNotNull();
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void getMyNotificationsShouldMapRepositoryData() {
        Notification notification = new Notification();
        notification.setId(20L);
        notification.setRecipientUserId(300L);
        notification.setType(NotificationType.MILESTONE_COMPLETED);
        notification.setTitle("Milestone completed");
        notification.setMessage("Milestone #1 completed");
        notification.setRead(false);
        notification.setCreatedAt(Instant.now());
        notification.setUpdatedAt(Instant.now());

        when(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(300L)).thenReturn(List.of(notification));

        List<NotificationResponse> responses = notificationService.getMyNotifications(
                new JwtUserPrincipal(300L, "client@example.com", "CLIENT")
        );

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(20L);
        assertThat(responses.get(0).type()).isEqualTo("MILESTONE_COMPLETED");
        assertThat(responses.get(0).title()).isEqualTo("Milestone completed");
    }
}
