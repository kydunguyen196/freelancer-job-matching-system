package com.skillbridge.contract_service.dto;

import java.time.Instant;
import java.util.List;

public record InternalContractSeriesResponse(
        Instant from,
        Instant to,
        String groupBy,
        String timezone,
        List<InternalContractSeriesPointResponse> points
) {
}
