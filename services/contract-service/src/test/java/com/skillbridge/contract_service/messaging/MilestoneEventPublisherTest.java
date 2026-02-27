package com.skillbridge.contract_service.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.skillbridge.common.events.EventTopics;
import com.skillbridge.common.events.MilestoneCompletedEvent;
import com.skillbridge.contract_service.domain.Contract;
import com.skillbridge.contract_service.domain.Milestone;

@ExtendWith(MockitoExtension.class)
class MilestoneEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private MilestoneEventPublisher milestoneEventPublisher;

    @Test
    void publishMilestoneCompletedShouldSendExpectedEvent() {
        Milestone milestone = milestone(1L, 10L, Instant.parse("2026-02-27T10:00:00Z"));
        Contract contract = contract(10L, 20L, 30L, 40L);

        milestoneEventPublisher.publishMilestoneCompleted(milestone, contract);

        verify(rabbitTemplate).convertAndSend(
                eq(EventTopics.EXCHANGE_NAME),
                eq(EventTopics.MILESTONE_COMPLETED_ROUTING_KEY),
                eq(new MilestoneCompletedEvent(1L, 10L, 20L, 30L, 40L, milestone.getCompletedAt()))
        );
    }

    @Test
    void publisherShouldSwallowAmqpException() {
        Milestone milestone = milestone(2L, 11L, Instant.parse("2026-02-27T11:00:00Z"));
        Contract contract = contract(11L, 21L, 31L, 41L);
        doThrow(new AmqpException("exchange unavailable")).when(rabbitTemplate).convertAndSend(
                eq(EventTopics.EXCHANGE_NAME),
                eq(EventTopics.MILESTONE_COMPLETED_ROUTING_KEY),
                eq(new MilestoneCompletedEvent(2L, 11L, 21L, 31L, 41L, milestone.getCompletedAt()))
        );

        assertThatCode(() -> milestoneEventPublisher.publishMilestoneCompleted(milestone, contract))
                .doesNotThrowAnyException();
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
