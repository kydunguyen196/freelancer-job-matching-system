package com.skillbridge.notification_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

import com.skillbridge.notification_service.config.EmailProperties;
import com.skillbridge.notification_service.domain.EmailDeliveryStatus;
import com.skillbridge.notification_service.domain.EmailDeliveryTask;
import com.skillbridge.notification_service.domain.NotificationType;
import com.skillbridge.notification_service.repository.EmailDeliveryTaskRepository;

@ExtendWith(MockitoExtension.class)
class EmailDeliveryServiceTest {

    @Mock
    private EmailDeliveryTaskRepository emailDeliveryTaskRepository;

    @Mock
    private SendGridEmailClient sendGridEmailClient;

    @Mock
    private TaskExecutor emailTaskExecutor;

    private EmailProperties emailProperties;

    private EmailDeliveryService emailDeliveryService;

    @BeforeEach
    void setUp() {
        emailProperties = new EmailProperties();
        emailProperties.setEnabled(true);
        emailProperties.setMaxAttempts(3);
        emailProperties.setInitialRetryDelaySeconds(60);
        emailProperties.setRetryMultiplier(2.0d);
        emailDeliveryService = new EmailDeliveryService(
                emailDeliveryTaskRepository,
                sendGridEmailClient,
                emailProperties,
                emailTaskExecutor
        );
    }

    @Test
    void enqueueEmailShouldSkipWhenDisabled() {
        emailProperties.setEnabled(false);

        emailDeliveryService.enqueueEmail(NotificationType.PROPOSAL_ACCEPTED, "freelancer@example.com", "Accepted", "Body");

        verify(emailDeliveryTaskRepository, never()).save(any(EmailDeliveryTask.class));
        verify(emailTaskExecutor, never()).execute(any(Runnable.class));
    }

    @Test
    void enqueueEmailShouldPersistPendingTaskAndDispatchAsync() {
        when(emailDeliveryTaskRepository.save(any(EmailDeliveryTask.class))).thenAnswer(invocation -> {
            EmailDeliveryTask task = invocation.getArgument(0);
            task.setId(10L);
            return task;
        });

        emailDeliveryService.enqueueEmail(NotificationType.PROPOSAL_REJECTED, "freelancer@example.com", "Rejected", "Body");

        ArgumentCaptor<EmailDeliveryTask> captor = ArgumentCaptor.forClass(EmailDeliveryTask.class);
        verify(emailDeliveryTaskRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EmailDeliveryStatus.PENDING);
        assertThat(captor.getValue().getRecipientEmail()).isEqualTo("freelancer@example.com");
        verify(emailTaskExecutor).execute(any(Runnable.class));
    }

    @Test
    void processTaskShouldMarkTaskSentOnSuccess() {
        EmailDeliveryTask task = new EmailDeliveryTask();
        task.setId(11L);
        task.setNotificationType(NotificationType.PROPOSAL_ACCEPTED.name());
        task.setRecipientEmail("freelancer@example.com");
        task.setSubject("Accepted");
        task.setBody("Body");
        task.setStatus(EmailDeliveryStatus.PENDING);
        task.setAttemptCount(0);
        task.setMaxAttempts(3);
        task.setNextAttemptAt(Instant.now().minusSeconds(1));

        when(emailDeliveryTaskRepository.findById(11L)).thenReturn(Optional.of(task));
        when(emailDeliveryTaskRepository.save(any(EmailDeliveryTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        emailDeliveryService.processTask(11L);

        verify(sendGridEmailClient).sendEmail("freelancer@example.com", "Accepted", "Body");
        assertThat(task.getStatus()).isEqualTo(EmailDeliveryStatus.SENT);
        assertThat(task.getSentAt()).isNotNull();
        assertThat(task.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void processTaskShouldScheduleRetryOnFailure() {
        EmailDeliveryTask task = new EmailDeliveryTask();
        task.setId(12L);
        task.setNotificationType(NotificationType.INTERVIEW_SCHEDULED.name());
        task.setRecipientEmail("freelancer@example.com");
        task.setSubject("Interview");
        task.setBody("Body");
        task.setStatus(EmailDeliveryStatus.PENDING);
        task.setAttemptCount(0);
        task.setMaxAttempts(3);
        task.setNextAttemptAt(Instant.now().minusSeconds(1));

        when(emailDeliveryTaskRepository.findById(12L)).thenReturn(Optional.of(task));
        when(emailDeliveryTaskRepository.save(any(EmailDeliveryTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.doThrow(new IllegalStateException("SendGrid unavailable"))
                .when(sendGridEmailClient).sendEmail("freelancer@example.com", "Interview", "Body");

        emailDeliveryService.processTask(12L);

        assertThat(task.getStatus()).isEqualTo(EmailDeliveryStatus.RETRY_PENDING);
        assertThat(task.getAttemptCount()).isEqualTo(1);
        assertThat(task.getNextAttemptAt()).isAfter(Instant.now());
        assertThat(task.getLastError()).contains("SendGrid unavailable");
    }
}
