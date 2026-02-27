package com.skillbridge.contract_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.contract_service.dto.MilestoneResponse;
import com.skillbridge.contract_service.security.JwtUserPrincipal;
import com.skillbridge.contract_service.service.ContractService;

import jakarta.validation.constraints.Min;

@Validated
@RestController
@RequestMapping("/milestones")
public class MilestoneController {

    private final ContractService contractService;

    public MilestoneController(ContractService contractService) {
        this.contractService = contractService;
    }

    @PatchMapping("/{milestoneId}/complete")
    public MilestoneResponse completeMilestone(
            @PathVariable @Min(1) Long milestoneId,
            Authentication authentication
    ) {
        return contractService.completeMilestone(milestoneId, extractPrincipal(authentication));
    }

    private JwtUserPrincipal extractPrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtUserPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal;
    }
}
