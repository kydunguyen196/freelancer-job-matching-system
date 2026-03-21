package com.skillbridge.proposal_service.dto;

import java.time.Instant;

public record InternalProposalSeriesPointResponse(
        Instant bucketStart,
        long proposals,
        long interviews,
        long accepted,
        long rejected
) {
}
