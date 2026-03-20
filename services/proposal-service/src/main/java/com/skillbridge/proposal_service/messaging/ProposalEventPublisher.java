package com.skillbridge.proposal_service.messaging;

import java.time.Instant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.skillbridge.common.events.EventTopics;
import com.skillbridge.common.events.ProposalAcceptedEvent;
import com.skillbridge.common.events.ProposalCreatedEvent;
import com.skillbridge.proposal_service.domain.ProposalOutboxEvent;
import com.skillbridge.proposal_service.domain.ProposalOutboxEventType;
import com.skillbridge.proposal_service.domain.Proposal;
import com.skillbridge.proposal_service.repository.ProposalOutboxEventRepository;

@Component
public class ProposalEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProposalEventPublisher.class);
    private static final String PROPOSAL_AGGREGATE_TYPE = "proposal";

    private final ProposalOutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public ProposalEventPublisher(
            ProposalOutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public void publishProposalCreated(Proposal proposal, Long clientId) {
        ProposalCreatedEvent event = new ProposalCreatedEvent(
                proposal.getId(),
                proposal.getJobId(),
                proposal.getFreelancerId(),
                clientId,
                proposal.getCreatedAt()
        );
        enqueue(
                proposal.getId(),
                ProposalOutboxEventType.PROPOSAL_CREATED,
                EventTopics.PROPOSAL_CREATED_ROUTING_KEY,
                event
        );
    }

    public void publishProposalAccepted(Proposal proposal, Long clientId) {
        ProposalAcceptedEvent event = new ProposalAcceptedEvent(
                proposal.getId(),
                proposal.getJobId(),
                clientId,
                proposal.getFreelancerId(),
                proposal.getFreelancerEmail(),
                proposal.getAcceptedAt()
        );
        enqueue(
                proposal.getId(),
                ProposalOutboxEventType.PROPOSAL_ACCEPTED,
                EventTopics.PROPOSAL_ACCEPTED_ROUTING_KEY,
                event
        );
    }

    private void enqueue(Long proposalId, ProposalOutboxEventType eventType, String routingKey, Object payload) {
        try {
            ProposalOutboxEvent event = new ProposalOutboxEvent();
            event.setAggregateType(PROPOSAL_AGGREGATE_TYPE);
            event.setAggregateId(proposalId);
            event.setEventType(eventType);
            event.setExchangeName(EventTopics.EXCHANGE_NAME);
            event.setRoutingKey(routingKey);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setAttempts(0);
            event.setNextAttemptAt(Instant.now());
            outboxEventRepository.save(event);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize outbox event type={} aggregateId={}: {}", eventType, proposalId, ex.getMessage());
        }
    }
}
