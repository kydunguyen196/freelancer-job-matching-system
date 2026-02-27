package com.skillbridge.proposal_service.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.skillbridge.common.events.EventTopics;
import com.skillbridge.common.events.ProposalAcceptedEvent;
import com.skillbridge.common.events.ProposalCreatedEvent;
import com.skillbridge.proposal_service.domain.Proposal;

@ExtendWith(MockitoExtension.class)
class ProposalEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private ProposalEventPublisher proposalEventPublisher;

    @Test
    void publishProposalCreatedShouldSendExpectedEvent() {
        Proposal proposal = proposal(100L, 200L, 300L);

        proposalEventPublisher.publishProposalCreated(proposal, 900L);

        verify(rabbitTemplate).convertAndSend(
                eq(EventTopics.EXCHANGE_NAME),
                eq(EventTopics.PROPOSAL_CREATED_ROUTING_KEY),
                eq(new ProposalCreatedEvent(100L, 200L, 300L, 900L, proposal.getCreatedAt()))
        );
    }

    @Test
    void publishProposalAcceptedShouldSendExpectedEvent() {
        Proposal proposal = proposal(101L, 201L, 301L);
        proposal.setAcceptedAt(Instant.parse("2026-02-27T10:15:30Z"));

        proposalEventPublisher.publishProposalAccepted(proposal, 901L);

        verify(rabbitTemplate).convertAndSend(
                eq(EventTopics.EXCHANGE_NAME),
                eq(EventTopics.PROPOSAL_ACCEPTED_ROUTING_KEY),
                eq(new ProposalAcceptedEvent(101L, 201L, 901L, 301L, proposal.getAcceptedAt()))
        );
    }

    @Test
    void publisherShouldSwallowAmqpException() {
        Proposal proposal = proposal(102L, 202L, 302L);
        doThrow(new AmqpException("broker unavailable")).when(rabbitTemplate).convertAndSend(
                eq(EventTopics.EXCHANGE_NAME),
                eq(EventTopics.PROPOSAL_CREATED_ROUTING_KEY),
                eq(new ProposalCreatedEvent(102L, 202L, 302L, 902L, proposal.getCreatedAt()))
        );

        assertThatCode(() -> proposalEventPublisher.publishProposalCreated(proposal, 902L))
                .doesNotThrowAnyException();
    }

    private Proposal proposal(Long proposalId, Long jobId, Long freelancerId) {
        Proposal proposal = new Proposal();
        proposal.setId(proposalId);
        proposal.setJobId(jobId);
        proposal.setFreelancerId(freelancerId);
        proposal.setPrice(BigDecimal.valueOf(100));
        proposal.setCreatedAt(Instant.parse("2026-02-27T08:00:00Z"));
        return proposal;
    }
}
