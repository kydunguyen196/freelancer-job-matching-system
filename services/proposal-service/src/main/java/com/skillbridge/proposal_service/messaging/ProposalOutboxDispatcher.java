package com.skillbridge.proposal_service.messaging;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.skillbridge.common.events.ProposalAcceptedEvent;
import com.skillbridge.common.events.ProposalCreatedEvent;
import com.skillbridge.proposal_service.domain.ProposalOutboxEvent;
import com.skillbridge.proposal_service.domain.ProposalOutboxEventType;
import com.skillbridge.proposal_service.repository.ProposalOutboxEventRepository;

@Component
public class ProposalOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ProposalOutboxDispatcher.class);
    private static final int LAST_ERROR_MAX_LENGTH = 2000;

    private final ProposalOutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final boolean enabled;
    private final int batchSize;
    private final long initialRetryDelaySeconds;
    private final double retryMultiplier;
    private final long maxRetryDelaySeconds;

    public ProposalOutboxDispatcher(
            ProposalOutboxEventRepository outboxEventRepository,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @Value("${app.outbox.enabled:true}") boolean enabled,
            @Value("${app.outbox.batch-size:50}") int batchSize,
            @Value("${app.outbox.initial-retry-delay-seconds:5}") long initialRetryDelaySeconds,
            @Value("${app.outbox.retry-multiplier:2.0}") double retryMultiplier,
            @Value("${app.outbox.max-retry-delay-seconds:300}") long maxRetryDelaySeconds
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.batchSize = Math.max(batchSize, 1);
        this.initialRetryDelaySeconds = Math.max(initialRetryDelaySeconds, 1);
        this.retryMultiplier = retryMultiplier < 1.0 ? 1.0 : retryMultiplier;
        this.maxRetryDelaySeconds = Math.max(maxRetryDelaySeconds, this.initialRetryDelaySeconds);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString = "${app.outbox.dispatch-interval-ms:5000}")
    public void dispatchPendingEvents() {
        if (!enabled) {
            return;
        }

        Instant now = Instant.now();
        Pageable batch = PageRequest.of(0, batchSize);
        List<Long> eventIds = outboxEventRepository
                .findByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(now, batch)
                .stream()
                .map(ProposalOutboxEvent::getId)
                .toList();

        for (Long eventId : eventIds) {
            transactionTemplate.executeWithoutResult(status -> dispatchSingleEvent(eventId));
        }
    }

    private void dispatchSingleEvent(Long eventId) {
        ProposalOutboxEvent event = outboxEventRepository.findByIdForUpdate(eventId).orElse(null);
        if (event == null || event.getPublishedAt() != null) {
            return;
        }

        Instant now = Instant.now();
        if (event.getNextAttemptAt() != null && event.getNextAttemptAt().isAfter(now)) {
            return;
        }

        try {
            Object payload = decodePayload(event);
            rabbitTemplate.convertAndSend(event.getExchangeName(), event.getRoutingKey(), payload);
            event.setPublishedAt(now);
            event.setLastError(null);
        } catch (Exception ex) {
            int attempts = event.getAttempts() + 1;
            event.setAttempts(attempts);
            event.setLastError(truncate(ex.getMessage()));
            event.setNextAttemptAt(now.plusSeconds(calculateDelaySeconds(attempts)));
            log.warn(
                    "Failed to dispatch proposal outbox event id={} type={} attempt={}: {}",
                    event.getId(),
                    event.getEventType(),
                    attempts,
                    ex.getMessage()
            );
        }
    }

    private Object decodePayload(ProposalOutboxEvent event) throws JsonProcessingException {
        return switch (event.getEventType()) {
            case PROPOSAL_CREATED -> objectMapper.readValue(event.getPayload(), ProposalCreatedEvent.class);
            case PROPOSAL_ACCEPTED -> objectMapper.readValue(event.getPayload(), ProposalAcceptedEvent.class);
        };
    }

    private long calculateDelaySeconds(int attempts) {
        double delay = initialRetryDelaySeconds * Math.pow(retryMultiplier, Math.max(0, attempts - 1));
        long bounded = Math.min((long) delay, maxRetryDelaySeconds);
        return Math.max(bounded, 1);
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() <= LAST_ERROR_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, LAST_ERROR_MAX_LENGTH);
    }
}
