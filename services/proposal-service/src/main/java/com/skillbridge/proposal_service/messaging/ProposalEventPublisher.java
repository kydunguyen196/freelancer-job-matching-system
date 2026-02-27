package com.skillbridge.proposal_service.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.skillbridge.common.events.EventTopics;
import com.skillbridge.common.events.ProposalAcceptedEvent;
import com.skillbridge.common.events.ProposalCreatedEvent;
import com.skillbridge.proposal_service.domain.Proposal;

@Component
public class ProposalEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProposalEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public ProposalEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishProposalCreated(Proposal proposal, Long clientId) {
        ProposalCreatedEvent event = new ProposalCreatedEvent(
                proposal.getId(),
                proposal.getJobId(),
                proposal.getFreelancerId(),
                clientId,
                proposal.getCreatedAt()
        );
        publish(EventTopics.PROPOSAL_CREATED_ROUTING_KEY, event, "ProposalCreatedEvent", proposal.getId());
    }

    public void publishProposalAccepted(Proposal proposal, Long clientId) {
        ProposalAcceptedEvent event = new ProposalAcceptedEvent(
                proposal.getId(),
                proposal.getJobId(),
                clientId,
                proposal.getFreelancerId(),
                proposal.getAcceptedAt()
        );
        publish(EventTopics.PROPOSAL_ACCEPTED_ROUTING_KEY, event, "ProposalAcceptedEvent", proposal.getId());
    }

    private void publish(String routingKey, Object payload, String eventName, Long proposalId) {
        try {
            rabbitTemplate.convertAndSend(EventTopics.EXCHANGE_NAME, routingKey, payload);
        } catch (AmqpException ex) {
            log.warn("Failed to publish {} for proposalId={}: {}", eventName, proposalId, ex.getMessage());
        }
    }
}
