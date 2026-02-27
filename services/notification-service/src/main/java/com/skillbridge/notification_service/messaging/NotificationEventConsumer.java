package com.skillbridge.notification_service.messaging;

import java.util.Objects;

import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.skillbridge.common.events.MilestoneCompletedEvent;
import com.skillbridge.common.events.ProposalAcceptedEvent;
import com.skillbridge.common.events.ProposalCreatedEvent;
import com.skillbridge.notification_service.config.RabbitMqConfig;
import com.skillbridge.notification_service.domain.NotificationType;
import com.skillbridge.notification_service.service.NotificationService;

@Component
@RabbitListener(queues = RabbitMqConfig.NOTIFICATION_QUEUE)
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final NotificationService notificationService;

    public NotificationEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitHandler
    public void handleProposalCreated(ProposalCreatedEvent event) {
        if (event == null) {
            log.warn("Received null ProposalCreatedEvent");
            return;
        }
        safeCreateNotification(
                event.clientId(),
                NotificationType.PROPOSAL_CREATED,
                "New proposal received",
                "A freelancer applied to your job #" + event.jobId(),
                "ProposalCreatedEvent",
                event.proposalId()
        );
    }

    @RabbitHandler
    public void handleProposalAccepted(ProposalAcceptedEvent event) {
        if (event == null) {
            log.warn("Received null ProposalAcceptedEvent");
            return;
        }
        safeCreateNotification(
                event.freelancerId(),
                NotificationType.PROPOSAL_ACCEPTED,
                "Proposal accepted",
                "Your proposal for job #" + event.jobId() + " has been accepted",
                "ProposalAcceptedEvent",
                event.proposalId()
        );
    }

    @RabbitHandler
    public void handleMilestoneCompleted(MilestoneCompletedEvent event) {
        if (event == null) {
            log.warn("Received null MilestoneCompletedEvent");
            return;
        }

        safeCreateNotification(
                event.clientId(),
                NotificationType.MILESTONE_COMPLETED,
                "Milestone completed",
                "Milestone #" + event.milestoneId() + " was completed for contract #" + event.contractId(),
                "MilestoneCompletedEvent",
                event.milestoneId()
        );
        if (!Objects.equals(event.clientId(), event.freelancerId())) {
            safeCreateNotification(
                    event.freelancerId(),
                    NotificationType.MILESTONE_COMPLETED,
                    "Milestone completed",
                    "Milestone #" + event.milestoneId() + " was completed for contract #" + event.contractId(),
                    "MilestoneCompletedEvent",
                    event.milestoneId()
            );
        }
    }

    @RabbitHandler(isDefault = true)
    public void handleUnsupportedEvent(Object event) {
        log.warn("Unsupported event payload in notification queue: {}", event);
    }

    private void safeCreateNotification(
            Long recipientUserId,
            NotificationType type,
            String title,
            String message,
            String eventName,
            Long eventRefId
    ) {
        try {
            notificationService.createNotification(recipientUserId, type, title, message);
        } catch (RuntimeException ex) {
            log.warn("Failed to consume {} for refId={}: {}", eventName, eventRefId, ex.getMessage());
        }
    }
}
