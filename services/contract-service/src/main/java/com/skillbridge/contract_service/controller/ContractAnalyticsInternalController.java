package com.skillbridge.contract_service.controller;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.contract_service.dto.InternalContractSeriesResponse;
import com.skillbridge.contract_service.dto.InternalContractSummaryResponse;
import com.skillbridge.contract_service.service.AnalyticsGroupBy;
import com.skillbridge.contract_service.service.ContractAnalyticsService;

import jakarta.validation.constraints.Min;

@Validated
@RestController
@RequestMapping("/contracts/internal/analytics")
public class ContractAnalyticsInternalController {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final ContractAnalyticsService contractAnalyticsService;
    private final String internalApiKey;

    public ContractAnalyticsInternalController(
            ContractAnalyticsService contractAnalyticsService,
            @Value("${app.internal.api-key}") String internalApiKey
    ) {
        this.contractAnalyticsService = contractAnalyticsService;
        this.internalApiKey = internalApiKey;
    }

    @GetMapping("/client/{clientId}/summary")
    public InternalContractSummaryResponse getClientSummary(
            @PathVariable @Min(1) Long clientId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestHeader(name = INTERNAL_API_KEY_HEADER, required = false) String providedApiKey
    ) {
        requireInternalApiKey(providedApiKey);
        return contractAnalyticsService.getClientSummary(clientId, from, to);
    }

    @GetMapping("/client/{clientId}/series")
    public InternalContractSeriesResponse getClientSeries(
            @PathVariable @Min(1) Long clientId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "DAY") AnalyticsGroupBy groupBy,
            @RequestParam(required = false) String timezone,
            @RequestHeader(name = INTERNAL_API_KEY_HEADER, required = false) String providedApiKey
    ) {
        requireInternalApiKey(providedApiKey);
        return contractAnalyticsService.getClientSeries(clientId, from, to, groupBy, timezone);
    }

    private void requireInternalApiKey(String providedApiKey) {
        if (providedApiKey == null || !providedApiKey.equals(internalApiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal API key");
        }
    }
}
