package com.skillbridge.common.events;

import java.time.Instant;

public record MilestoneCompletedEvent(
        Long milestoneId,
        Long contractId,
        Long jobId,
        Long clientId,
        Long freelancerId,
        Instant completedAt
) {
}
