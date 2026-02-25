package com.skillbridge.proposal_service.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.proposal_service.dto.CreateProposalRequest;
import com.skillbridge.proposal_service.dto.ProposalCreateResponse;
import com.skillbridge.proposal_service.security.JwtUserPrincipal;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/proposals")
public class ProposalController {

    @GetMapping
    public List<Map<String, Object>> listProposals() {
        return List.of(
                Map.of(
                        "id", 0,
                        "message", "Sample proposal endpoint (Day 4 security stub)",
                        "status", "PENDING"
                )
        );
    }

    @PostMapping
    public ResponseEntity<ProposalCreateResponse> createProposal(
            @Valid @RequestBody CreateProposalRequest request,
            Authentication authentication
    ) {
        JwtUserPrincipal principal = extractPrincipal(authentication);
        if (!"FREELANCER".equalsIgnoreCase(principal.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only FREELANCER can create proposals");
        }

        ProposalCreateResponse response = new ProposalCreateResponse(
                "Day 4 secure stub: FREELANCER authorization passed for proposal creation",
                principal.userId(),
                principal.email(),
                principal.role(),
                request.jobId(),
                request.price(),
                request.durationDays()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private JwtUserPrincipal extractPrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtUserPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal;
    }
}
