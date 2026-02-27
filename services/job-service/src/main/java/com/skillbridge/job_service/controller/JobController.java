package com.skillbridge.job_service.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import com.skillbridge.job_service.domain.JobStatus;
import com.skillbridge.job_service.dto.CreateJobRequest;
import com.skillbridge.job_service.dto.JobResponse;
import com.skillbridge.job_service.dto.PagedResult;
import com.skillbridge.job_service.security.JwtUserPrincipal;
import com.skillbridge.job_service.service.JobService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

@Validated
@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    public ResponseEntity<JobResponse> createJob(
            @Valid @RequestBody CreateJobRequest request,
            Authentication authentication
    ) {
        JwtUserPrincipal principal = extractPrincipal(authentication);
        return ResponseEntity.status(201).body(jobService.createJob(request, principal));
    }

    @GetMapping
    public ResponseEntity<List<JobResponse>> listJobs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) @DecimalMin("0.01") BigDecimal budgetMin,
            @RequestParam(required = false) @DecimalMin("0.01") BigDecimal budgetMax,
            @RequestParam(required = false) @Min(1) Long clientId,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) Integer size
    ) {
        PagedResult<JobResponse> result = jobService.listJobs(keyword, status, budgetMin, budgetMax, clientId, tags, page, size);
        HttpHeaders headers = buildPagingHeaders(result);
        return ResponseEntity.ok().headers(headers).body(result.content());
    }

    @GetMapping("/{jobId}")
    public JobResponse getJobById(@PathVariable @Min(1) Long jobId) {
        return jobService.getJobById(jobId);
    }

    @PatchMapping("/{jobId}/close")
    public JobResponse closeJob(@PathVariable @Min(1) Long jobId, Authentication authentication) {
        JwtUserPrincipal principal = extractPrincipal(authentication);
        return jobService.closeJob(jobId, principal);
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
