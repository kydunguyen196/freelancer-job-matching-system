package com.skillbridge.contract_service.dto;

public record InternalContractSummaryResponse(
        long totalContracts,
        long activeContracts,
        long completedContracts,
        long cancelledContracts
) {
}
