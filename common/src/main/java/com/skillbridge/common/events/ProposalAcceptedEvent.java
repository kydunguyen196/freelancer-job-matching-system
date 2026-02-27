package com.skillbridge.common.events;

import java.time.Instant;

public record ProposalAcceptedEvent(
        Long proposalId,
        Long jobId,
        Long clientId,
        Long freelancerId,
        Instant acceptedAt
) {
}
