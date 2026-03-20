package com.skillbridge.proposal_service.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.skillbridge.common.events.EventTopics;
import com.skillbridge.proposal_service.domain.Proposal;
import com.skillbridge.proposal_service.domain.ProposalOutboxEvent;
import com.skillbridge.proposal_service.domain.ProposalOutboxEventType;
import com.skillbridge.proposal_service.repository.ProposalOutboxEventRepository;

@ExtendWith(MockitoExtension.class)
class ProposalEventPublisherTest {

    @Mock
    private ProposalOutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ProposalEventPublisher proposalEventPublisher;

    @Test
    void publishProposalCreatedShouldStoreOutboxEvent() throws Exception {
        Proposal proposal = proposal(100L, 200L, 300L);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"type\":\"created\"}");

        proposalEventPublisher.publishProposalCreated(proposal, 900L);

        ArgumentCaptor<ProposalOutboxEvent> captor = ArgumentCaptor.forClass(ProposalOutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        ProposalOutboxEvent stored = captor.getValue();
        assertThat(stored.getAggregateType()).isEqualTo("proposal");
        assertThat(stored.getAggregateId()).isEqualTo(100L);
        assertThat(stored.getEventType()).isEqualTo(ProposalOutboxEventType.PROPOSAL_CREATED);
        assertThat(stored.getExchangeName()).isEqualTo(EventTopics.EXCHANGE_NAME);
        assertThat(stored.getRoutingKey()).isEqualTo(EventTopics.PROPOSAL_CREATED_ROUTING_KEY);
        assertThat(stored.getPayload()).isEqualTo("{\"type\":\"created\"}");
        assertThat(stored.getAttempts()).isZero();
        assertThat(stored.getNextAttemptAt()).isNotNull();
    }

    @Test
    void publishProposalAcceptedShouldStoreOutboxEvent() throws Exception {
        Proposal proposal = proposal(101L, 201L, 301L);
        proposal.setFreelancerEmail("freelancer@example.com");
        proposal.setAcceptedAt(Instant.parse("2026-02-27T10:15:30Z"));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"type\":\"accepted\"}");

        proposalEventPublisher.publishProposalAccepted(proposal, 901L);

        ArgumentCaptor<ProposalOutboxEvent> captor = ArgumentCaptor.forClass(ProposalOutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        ProposalOutboxEvent stored = captor.getValue();
        assertThat(stored.getEventType()).isEqualTo(ProposalOutboxEventType.PROPOSAL_ACCEPTED);
        assertThat(stored.getRoutingKey()).isEqualTo(EventTopics.PROPOSAL_ACCEPTED_ROUTING_KEY);
    }

    @Test
    void publishShouldSwallowSerializationFailure() throws Exception {
        Proposal proposal = proposal(102L, 202L, 302L);
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("broken json") {});

        assertThatCode(() -> proposalEventPublisher.publishProposalCreated(proposal, 902L))
                .doesNotThrowAnyException();
        verify(outboxEventRepository, never()).save(any());
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
