package com.skillbridge.contract_service.messaging;

import java.time.Instant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.skillbridge.common.events.EventTopics;
import com.skillbridge.common.events.MilestoneCompletedEvent;
import com.skillbridge.contract_service.domain.Contract;
import com.skillbridge.contract_service.domain.ContractOutboxEvent;
import com.skillbridge.contract_service.domain.ContractOutboxEventType;
import com.skillbridge.contract_service.domain.Milestone;
import com.skillbridge.contract_service.repository.ContractOutboxEventRepository;

@Component
public class MilestoneEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(MilestoneEventPublisher.class);
    private static final String MILESTONE_AGGREGATE_TYPE = "milestone";

    private final ContractOutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public MilestoneEventPublisher(
            ContractOutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public void publishMilestoneCompleted(Milestone milestone, Contract contract) {
        MilestoneCompletedEvent event = new MilestoneCompletedEvent(
                milestone.getId(),
                contract.getId(),
                contract.getJobId(),
                contract.getClientId(),
                contract.getFreelancerId(),
                milestone.getCompletedAt()
        );
        try {
            ContractOutboxEvent outboxEvent = new ContractOutboxEvent();
            outboxEvent.setAggregateType(MILESTONE_AGGREGATE_TYPE);
            outboxEvent.setAggregateId(milestone.getId());
            outboxEvent.setEventType(ContractOutboxEventType.MILESTONE_COMPLETED);
            outboxEvent.setExchangeName(EventTopics.EXCHANGE_NAME);
            outboxEvent.setRoutingKey(EventTopics.MILESTONE_COMPLETED_ROUTING_KEY);
            outboxEvent.setPayload(objectMapper.writeValueAsString(event));
            outboxEvent.setAttempts(0);
            outboxEvent.setNextAttemptAt(Instant.now());
            outboxEventRepository.save(outboxEvent);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize MilestoneCompletedEvent for milestoneId={}: {}", milestone.getId(), ex.getMessage());
        }
    }
}
