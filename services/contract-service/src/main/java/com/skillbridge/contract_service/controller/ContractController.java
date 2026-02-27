package com.skillbridge.contract_service.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.contract_service.dto.ContractResponse;
import com.skillbridge.contract_service.dto.CreateContractFromProposalRequest;
import com.skillbridge.contract_service.dto.CreateMilestoneRequest;
import com.skillbridge.contract_service.security.JwtUserPrincipal;
import com.skillbridge.contract_service.service.ContractService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

@Validated
@RestController
@RequestMapping("/contracts")
public class ContractController {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final ContractService contractService;
    private final String internalApiKey;

    public ContractController(
            ContractService contractService,
            @Value("${app.internal.api-key}") String internalApiKey
    ) {
        this.contractService = contractService;
        this.internalApiKey = internalApiKey;
    }

    @GetMapping("/me")
    public List<ContractResponse> getMyContracts(Authentication authentication) {
        return contractService.getMyContracts(extractPrincipal(authentication));
    }

    @PostMapping("/{contractId}/milestones")
    public ContractResponse addMilestone(
            @PathVariable @Min(1) Long contractId,
            @Valid @RequestBody CreateMilestoneRequest request,
            Authentication authentication
    ) {
        return contractService.addMilestone(contractId, request, extractPrincipal(authentication));
    }

    @PostMapping("/internal/from-proposal")
    public ContractResponse createFromProposal(
            @RequestHeader(value = INTERNAL_API_KEY_HEADER, required = false) String providedKey,
            @Valid @RequestBody CreateContractFromProposalRequest request
    ) {
        if (!internalApiKey.equals(providedKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal api key");
        }
        return contractService.createContractFromProposal(request);
    }

    private JwtUserPrincipal extractPrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtUserPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal;
    }
}
