package com.skillbridge.job_service.controller;

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

import com.skillbridge.job_service.dto.CreateJobRequest;
import com.skillbridge.job_service.dto.JobCreateResponse;
import com.skillbridge.job_service.security.JwtUserPrincipal;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/jobs")
public class JobController {

    @GetMapping
    public List<Map<String, Object>> listJobs() {
        return List.of(
                Map.of(
                        "id", 0,
                        "title", "Sample public job list endpoint (Day 4 security stub)",
                        "status", "OPEN"
                )
        );
    }

    @PostMapping
    public ResponseEntity<JobCreateResponse> createJob(
            @Valid @RequestBody CreateJobRequest request,
            Authentication authentication
    ) {
        JwtUserPrincipal principal = extractPrincipal(authentication);
        if (!"CLIENT".equalsIgnoreCase(principal.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only CLIENT can create jobs");
        }

        JobCreateResponse response = new JobCreateResponse(
                "Day 4 secure stub: CLIENT authorization passed for job creation",
                principal.userId(),
                principal.email(),
                principal.role(),
                request.title(),
                request.budgetMin(),
                request.budgetMax()
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
