package com.skillbridge.proposal_service.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.skillbridge.proposal_service.dto.InternalProposalSeriesResponse;
import com.skillbridge.proposal_service.dto.InternalProposalSummaryResponse;
import com.skillbridge.proposal_service.dto.InternalTopJobProposalPerformanceResponse;
import com.skillbridge.proposal_service.service.AnalyticsGroupBy;
import com.skillbridge.proposal_service.service.ProposalAnalyticsService;

import jakarta.validation.constraints.Min;

@Validated
@RestController
@RequestMapping("/proposals/internal/analytics")
public class ProposalAnalyticsInternalController {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final ProposalAnalyticsService proposalAnalyticsService;
    private final String internalApiKey;

    public ProposalAnalyticsInternalController(
            ProposalAnalyticsService proposalAnalyticsService,
            @Value("${app.internal.api-key}") String internalApiKey
    ) {
        this.proposalAnalyticsService = proposalAnalyticsService;
        this.internalApiKey = internalApiKey;
    }

    @GetMapping("/client/{clientId}/summary")
    public InternalProposalSummaryResponse getClientSummary(
            @PathVariable @Min(1) Long clientId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestHeader(name = INTERNAL_API_KEY_HEADER, required = false) String providedApiKey
    ) {
        requireInternalApiKey(providedApiKey);
        return proposalAnalyticsService.getClientSummary(clientId, from, to);
    }

    @GetMapping("/client/{clientId}/series")
    public InternalProposalSeriesResponse getClientSeries(
            @PathVariable @Min(1) Long clientId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "DAY") AnalyticsGroupBy groupBy,
            @RequestParam(required = false) String timezone,
            @RequestHeader(name = INTERNAL_API_KEY_HEADER, required = false) String providedApiKey
    ) {
        requireInternalApiKey(providedApiKey);
        return proposalAnalyticsService.getClientSeries(clientId, from, to, groupBy, timezone);
    }

    @GetMapping("/client/{clientId}/top-jobs")
    public List<InternalTopJobProposalPerformanceResponse> getClientTopJobs(
            @PathVariable @Min(1) Long clientId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "5") @Min(1) Integer limit,
            @RequestHeader(name = INTERNAL_API_KEY_HEADER, required = false) String providedApiKey
    ) {
        requireInternalApiKey(providedApiKey);
        return proposalAnalyticsService.getClientTopJobs(clientId, from, to, limit);
    }

    private void requireInternalApiKey(String providedApiKey) {
        if (providedApiKey == null || !providedApiKey.equals(internalApiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal API key");
        }
    }
}
