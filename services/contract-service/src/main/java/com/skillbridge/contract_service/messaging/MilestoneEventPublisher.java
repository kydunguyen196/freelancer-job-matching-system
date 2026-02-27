package com.skillbridge.contract_service.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.skillbridge.common.events.EventTopics;
import com.skillbridge.common.events.MilestoneCompletedEvent;
import com.skillbridge.contract_service.domain.Contract;
import com.skillbridge.contract_service.domain.Milestone;

@Component
public class MilestoneEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(MilestoneEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public MilestoneEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
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
            rabbitTemplate.convertAndSend(
                    EventTopics.EXCHANGE_NAME,
                    EventTopics.MILESTONE_COMPLETED_ROUTING_KEY,
                    event
            );
        } catch (AmqpException ex) {
            log.warn("Failed to publish MilestoneCompletedEvent for milestoneId={}: {}", milestone.getId(), ex.getMessage());
        }
    }
}
