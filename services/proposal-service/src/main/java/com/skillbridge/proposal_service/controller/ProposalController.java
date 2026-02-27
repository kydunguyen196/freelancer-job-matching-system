package com.skillbridge.proposal_service.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.proposal_service.dto.CreateProposalRequest;
import com.skillbridge.proposal_service.dto.PagedResult;
import com.skillbridge.proposal_service.dto.ProposalResponse;
import com.skillbridge.proposal_service.security.JwtUserPrincipal;
import com.skillbridge.proposal_service.service.ProposalService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

@Validated
@RestController
public class ProposalController {

    private final ProposalService proposalService;

    public ProposalController(ProposalService proposalService) {
        this.proposalService = proposalService;
    }

    @PostMapping("/proposals")
    public ResponseEntity<ProposalResponse> createProposal(
            @Valid @RequestBody CreateProposalRequest request,
            Authentication authentication
    ) {
        JwtUserPrincipal principal = extractPrincipal(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(proposalService.createProposal(request, principal));
    }

    @GetMapping("/jobs/{jobId}/proposals")
    public ResponseEntity<List<ProposalResponse>> listProposalsByJob(
            @PathVariable @Min(1) Long jobId,
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) Integer size,
            Authentication authentication
    ) {
        JwtUserPrincipal principal = extractPrincipal(authentication);
        PagedResult<ProposalResponse> result = proposalService.listProposalsByJob(jobId, principal, page, size);
        HttpHeaders headers = buildPagingHeaders(result);
        return ResponseEntity.ok().headers(headers).body(result.content());
    }

    @PatchMapping("/proposals/{proposalId}/accept")
    public ProposalResponse acceptProposal(
            @PathVariable @Min(1) Long proposalId,
            Authentication authentication
    ) {
        JwtUserPrincipal principal = extractPrincipal(authentication);
        return proposalService.acceptProposal(proposalId, principal);
    }

    private JwtUserPrincipal extractPrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtUserPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal;
    }

    private HttpHeaders buildPagingHeaders(PagedResult<?> result) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Page", String.valueOf(result.page()));
        headers.add("X-Size", String.valueOf(result.size()));
        headers.add("X-Total-Elements", String.valueOf(result.totalElements()));
        headers.add("X-Total-Pages", String.valueOf(result.totalPages()));
        return headers;
    }
}
