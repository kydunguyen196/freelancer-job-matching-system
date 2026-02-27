package com.skillbridge.common.events;

import java.time.Instant;

public record ProposalCreatedEvent(
        Long proposalId,
        Long jobId,
        Long freelancerId,
        Long clientId,
        Instant createdAt
) {
}
