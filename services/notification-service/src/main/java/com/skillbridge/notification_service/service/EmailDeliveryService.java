package com.skillbridge.notification_service.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skillbridge.notification_service.config.EmailProperties;
import com.skillbridge.notification_service.domain.EmailDeliveryStatus;
import com.skillbridge.notification_service.domain.EmailDeliveryTask;
import com.skillbridge.notification_service.domain.NotificationType;
import com.skillbridge.notification_service.repository.EmailDeliveryTaskRepository;

@Service
public class EmailDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryService.class);

    private final EmailDeliveryTaskRepository emailDeliveryTaskRepository;
    private final SendGridEmailClient sendGridEmailClient;
    private final EmailProperties emailProperties;
    private final TaskExecutor emailTaskExecutor;

    public EmailDeliveryService(
            EmailDeliveryTaskRepository emailDeliveryTaskRepository,
            SendGridEmailClient sendGridEmailClient,
            EmailProperties emailProperties,
            @Qualifier("emailTaskExecutor") TaskExecutor emailTaskExecutor
    ) {
        this.emailDeliveryTaskRepository = emailDeliveryTaskRepository;
        this.sendGridEmailClient = sendGridEmailClient;
        this.emailProperties = emailProperties;
        this.emailTaskExecutor = emailTaskExecutor;
    }

    @Transactional
    public void enqueueEmail(NotificationType type, String recipientEmail, String subject, String body) {
        if (!emailProperties.isEnabled()) {
            return;
        }
        if (!supportsEmail(type) || recipientEmail == null || recipientEmail.isBlank()) {
            return;
        }

        EmailDeliveryTask task = new EmailDeliveryTask();
        task.setNotificationType(type.name());
        task.setRecipientEmail(recipientEmail.trim());
        task.setSubject(subject);
        task.setBody(body);
        task.setStatus(EmailDeliveryStatus.PENDING);
        task.setAttemptCount(0);
        task.setMaxAttempts(Math.max(1, emailProperties.getMaxAttempts()));
        task.setNextAttemptAt(Instant.now());
        EmailDeliveryTask savedTask = emailDeliveryTaskRepository.save(task);
        dispatchAsync(savedTask.getId());
    }

    public void dispatchAsync(Long taskId) {
        emailTaskExecutor.execute(() -> processTask(taskId));
    }

    @Scheduled(fixedDelayString = "${app.email.retry-poll-delay-ms:60000}")
    public void retryPendingEmails() {
        if (!emailProperties.isEnabled()) {
            return;
        }
        List<EmailDeliveryTask> tasks = emailDeliveryTaskRepository
                .findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                        List.of(EmailDeliveryStatus.PENDING, EmailDeliveryStatus.RETRY_PENDING),
                        Instant.now()
                );
        tasks.forEach(task -> dispatchAsync(task.getId()));
    }

    @Transactional
    public void processTask(Long taskId) {
        EmailDeliveryTask task = emailDeliveryTaskRepository.findById(taskId).orElse(null);
        if (task == null || !isDue(task)) {
            return;
        }

        task.setStatus(EmailDeliveryStatus.PROCESSING);
        emailDeliveryTaskRepository.save(task);

        try {
            sendGridEmailClient.sendEmail(task.getRecipientEmail(), task.getSubject(), task.getBody());
            task.setStatus(EmailDeliveryStatus.SENT);
            task.setSentAt(Instant.now());
            task.setLastError(null);
            task.setAttemptCount(task.getAttemptCount() + 1);
            emailDeliveryTaskRepository.save(task);
        } catch (Exception ex) {
            scheduleRetry(task, ex);
        }
    }

    private boolean isDue(EmailDeliveryTask task) {
        return (task.getStatus() == EmailDeliveryStatus.PENDING || task.getStatus() == EmailDeliveryStatus.RETRY_PENDING)
                && !task.getNextAttemptAt().isAfter(Instant.now());
    }

    private void scheduleRetry(EmailDeliveryTask task, Exception ex) {
        int nextAttempt = task.getAttemptCount() + 1;
        task.setAttemptCount(nextAttempt);
        task.setLastError(truncate(ex.getMessage(), 1000));

        if (nextAttempt >= task.getMaxAttempts()) {
            task.setStatus(EmailDeliveryStatus.FAILED);
            emailDeliveryTaskRepository.save(task);
            log.warn("Email delivery failed permanently for taskId={} recipient={}: {}", task.getId(), task.getRecipientEmail(), ex.getMessage());
            return;
        }

        task.setStatus(EmailDeliveryStatus.RETRY_PENDING);
        task.setNextAttemptAt(Instant.now().plus(calculateBackoff(nextAttempt)));
        emailDeliveryTaskRepository.save(task);
        log.warn("Email delivery failed for taskId={} recipient={} attempt={}/{}: {}",
                task.getId(),
                task.getRecipientEmail(),
                nextAttempt,
                task.getMaxAttempts(),
                ex.getMessage());
    }

    private Duration calculateBackoff(int attempt) {
        double multiplier = Math.max(1.0d, emailProperties.getRetryMultiplier());
        double seconds = emailProperties.getInitialRetryDelaySeconds() * Math.pow(multiplier, Math.max(0, attempt - 1));
        return Duration.ofSeconds(Math.max(1L, (long) seconds));
    }

    private boolean supportsEmail(NotificationType type) {
        return type == NotificationType.PROPOSAL_ACCEPTED
                || type == NotificationType.PROPOSAL_REJECTED
                || type == NotificationType.INTERVIEW_SCHEDULED;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
