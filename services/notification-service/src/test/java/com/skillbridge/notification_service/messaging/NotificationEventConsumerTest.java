package com.skillbridge.notification_service.messaging;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.common.events.MilestoneCompletedEvent;
import com.skillbridge.common.events.ProposalAcceptedEvent;
import com.skillbridge.common.events.ProposalCreatedEvent;
import com.skillbridge.notification_service.domain.NotificationType;
import com.skillbridge.notification_service.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationEventConsumer notificationEventConsumer;

    @Test
    void handleProposalCreatedShouldCreateClientNotification() {
        ProposalCreatedEvent event = new ProposalCreatedEvent(10L, 20L, 30L, 40L, Instant.now());

        notificationEventConsumer.handleProposalCreated(event);

        verify(notificationService).createNotification(
                40L,
                NotificationType.PROPOSAL_CREATED,
                "New proposal received",
                "A freelancer applied to your job #20"
        );
    }

    @Test
    void handleProposalAcceptedShouldCreateFreelancerNotification() {
        ProposalAcceptedEvent event = new ProposalAcceptedEvent(11L, 22L, 33L, 44L, Instant.now());

        notificationEventConsumer.handleProposalAccepted(event);

        verify(notificationService).createNotification(
                44L,
                NotificationType.PROPOSAL_ACCEPTED,
                "Proposal accepted",
                "Your proposal for job #22 has been accepted"
        );
    }

    @Test
    void handleMilestoneCompletedShouldCreateTwoNotificationsWhenParticipantsDiffer() {
        MilestoneCompletedEvent event = new MilestoneCompletedEvent(99L, 88L, 77L, 10L, 11L, Instant.now());

        notificationEventConsumer.handleMilestoneCompleted(event);

        verify(notificationService, times(2)).createNotification(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(NotificationType.MILESTONE_COMPLETED),
                org.mockito.ArgumentMatchers.eq("Milestone completed"),
                org.mockito.ArgumentMatchers.eq("Milestone #99 was completed for contract #88")
        );
    }

    @Test
    void handleMilestoneCompletedShouldCreateSingleNotificationWhenSameRecipient() {
        MilestoneCompletedEvent event = new MilestoneCompletedEvent(99L, 88L, 77L, 10L, 10L, Instant.now());

        notificationEventConsumer.handleMilestoneCompleted(event);

        verify(notificationService, times(1)).createNotification(
                10L,
                NotificationType.MILESTONE_COMPLETED,
                "Milestone completed",
                "Milestone #99 was completed for contract #88"
        );
    }

    @Test
    void consumerShouldIgnoreNullEvents() {
        notificationEventConsumer.handleProposalCreated(null);
        notificationEventConsumer.handleProposalAccepted(null);
        notificationEventConsumer.handleMilestoneCompleted(null);

        verifyNoInteractions(notificationService);
    }

    @Test
    void consumerShouldNotThrowWhenNotificationCreationFails() {
        ProposalCreatedEvent event = new ProposalCreatedEvent(10L, 20L, 30L, 0L, Instant.now());
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "recipientUserId must be greater than 0"))
                .when(notificationService).createNotification(
                0L,
                NotificationType.PROPOSAL_CREATED,
                "New proposal received",
                "A freelancer applied to your job #20"
        );

        notificationEventConsumer.handleProposalCreated(event);

        verify(notificationService).createNotification(
                0L,
                NotificationType.PROPOSAL_CREATED,
                "New proposal received",
                "A freelancer applied to your job #20"
        );
    }

    @Test
    void handleUnsupportedEventShouldNotCallService() {
        notificationEventConsumer.handleUnsupportedEvent("unknown");
        verify(notificationService, never()).createNotification(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }
}
