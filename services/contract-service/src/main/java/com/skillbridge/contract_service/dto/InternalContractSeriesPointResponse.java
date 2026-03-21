package com.skillbridge.contract_service.dto;

import java.time.Instant;

public record InternalContractSeriesPointResponse(
        Instant bucketStart,
        long contractsCreated,
        long contractsCompleted
) {
}
