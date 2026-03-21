package com.skillbridge.proposal_service.dto;

import java.time.Instant;
import java.util.List;

public record InternalProposalSeriesResponse(
        Instant from,
        Instant to,
        String groupBy,
        String timezone,
        List<InternalProposalSeriesPointResponse> points
) {
}
