package com.skillbridge.contract_service.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.skillbridge.common.events.EventTopics;
import com.skillbridge.contract_service.domain.Contract;
import com.skillbridge.contract_service.domain.ContractOutboxEvent;
import com.skillbridge.contract_service.domain.ContractOutboxEventType;
import com.skillbridge.contract_service.domain.Milestone;
import com.skillbridge.contract_service.repository.ContractOutboxEventRepository;

@ExtendWith(MockitoExtension.class)
class MilestoneEventPublisherTest {

    @Mock
    private ContractOutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private MilestoneEventPublisher milestoneEventPublisher;

    @Test
    void publishMilestoneCompletedShouldStoreOutboxEvent() throws Exception {
        Milestone milestone = milestone(1L, 10L, Instant.parse("2026-02-27T10:00:00Z"));
        Contract contract = contract(10L, 20L, 30L, 40L);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"type\":\"milestone-completed\"}");

        milestoneEventPublisher.publishMilestoneCompleted(milestone, contract);

        ArgumentCaptor<ContractOutboxEvent> captor = ArgumentCaptor.forClass(ContractOutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        ContractOutboxEvent stored = captor.getValue();
        assertThat(stored.getAggregateType()).isEqualTo("milestone");
        assertThat(stored.getAggregateId()).isEqualTo(1L);
        assertThat(stored.getEventType()).isEqualTo(ContractOutboxEventType.MILESTONE_COMPLETED);
        assertThat(stored.getExchangeName()).isEqualTo(EventTopics.EXCHANGE_NAME);
        assertThat(stored.getRoutingKey()).isEqualTo(EventTopics.MILESTONE_COMPLETED_ROUTING_KEY);
        assertThat(stored.getPayload()).isEqualTo("{\"type\":\"milestone-completed\"}");
        assertThat(stored.getAttempts()).isZero();
        assertThat(stored.getNextAttemptAt()).isNotNull();
    }

    @Test
    void publisherShouldSwallowSerializationFailure() throws Exception {
        Milestone milestone = milestone(2L, 11L, Instant.parse("2026-02-27T11:00:00Z"));
        Contract contract = contract(11L, 21L, 31L, 41L);
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("broken json") {});

        assertThatCode(() -> milestoneEventPublisher.publishMilestoneCompleted(milestone, contract))
                .doesNotThrowAnyException();
        verify(outboxEventRepository, never()).save(any());
    }

    private Contract contract(Long contractId, Long jobId, Long clientId, Long freelancerId) {
        Contract contract = new Contract();
        contract.setId(contractId);
        contract.setJobId(jobId);
        contract.setClientId(clientId);
        contract.setFreelancerId(freelancerId);
        return contract;
    }

    private Milestone milestone(Long milestoneId, Long contractId, Instant completedAt) {
        Milestone milestone = new Milestone();
        milestone.setId(milestoneId);
        milestone.setContractId(contractId);
        milestone.setTitle("Phase 1");
        milestone.setDueDate(LocalDate.now().plusDays(3));
        milestone.setCompletedAt(completedAt);
        return milestone;
    }
}
