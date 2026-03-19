package com.skillbridge.job_service.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.job_service.domain.EmploymentType;
import com.skillbridge.job_service.domain.JobStatus;
import com.skillbridge.job_service.dto.CreateJobRequest;
import com.skillbridge.job_service.dto.CompanySearchResponse;
import com.skillbridge.job_service.dto.FollowedCompanyResponse;
import com.skillbridge.job_service.dto.JobDashboardResponse;
import com.skillbridge.job_service.dto.JobResponse;
import com.skillbridge.job_service.dto.JobSearchReindexResponse;
import com.skillbridge.job_service.dto.JobSearchSuggestionResponse;
import com.skillbridge.job_service.dto.PagedResult;
import com.skillbridge.job_service.dto.UpdateJobStatusRequest;
import com.skillbridge.job_service.security.JwtUserPrincipal;
import com.skillbridge.job_service.service.JobSearchAdminService;
import com.skillbridge.job_service.service.JobService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

@Validated
@RestController
@RequestMapping("/jobs")
public class JobController {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final JobService jobService;
    private final JobSearchAdminService jobSearchAdminService;
    private final String internalApiKey;

    public JobController(
            JobService jobService,
            JobSearchAdminService jobSearchAdminService,
            @Value("${app.internal.api-key}") String internalApiKey
    ) {
        this.jobService = jobService;
        this.jobSearchAdminService = jobSearchAdminService;
        this.internalApiKey = internalApiKey;
    }

    @PostMapping
    public ResponseEntity<JobResponse> createJob(
            @Valid @RequestBody CreateJobRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jobService.createJob(request, extractRequiredPrincipal(authentication)));
    }

    @GetMapping
    public ResponseEntity<List<JobResponse>> listJobs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) @DecimalMin("0.01") BigDecimal budgetMin,
            @RequestParam(required = false) @DecimalMin("0.01") BigDecimal budgetMax,
            @RequestParam(required = false) @Min(1) Long clientId,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) EmploymentType employmentType,
            @RequestParam(required = false) Boolean remote,
            @RequestParam(required = false) @Min(0) Integer experienceYearsMin,
            @RequestParam(required = false) @Min(0) Integer experienceYearsMax,
            @RequestParam(defaultValue = "latest") String sortBy,
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) Integer size,
            Authentication authentication
    ) {
        PagedResult<JobResponse> result = jobService.listJobs(
                keyword,
                status,
                budgetMin,
                budgetMax,
                clientId,
                tags,
                location,
                companyName,
                employmentType,
                remote,
                experienceYearsMin,
                experienceYearsMax,
                sortBy,
                page,
                size,
                extractOptionalPrincipal(authentication)
        );
        return ResponseEntity.ok().headers(buildPagingHeaders(result)).body(result.content());
    }

    @GetMapping("/me")
    public ResponseEntity<List<JobResponse>> listMyJobs(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(defaultValue = "latest") String sortBy,
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) Integer size,
            Authentication authentication
    ) {
        PagedResult<JobResponse> result = jobService.listMyJobs(
                status,
                sortBy,
                page,
                size,
                extractRequiredPrincipal(authentication)
        );
        return ResponseEntity.ok().headers(buildPagingHeaders(result)).body(result.content());
    }

    @GetMapping("/saved/me")
    public ResponseEntity<List<JobResponse>> listSavedJobs(
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) Integer size,
            Authentication authentication
    ) {
        PagedResult<JobResponse> result = jobService.listSavedJobs(page, size, extractRequiredPrincipal(authentication));
        return ResponseEntity.ok().headers(buildPagingHeaders(result)).body(result.content());
    }

    @GetMapping("/companies/following/me")
    public ResponseEntity<List<FollowedCompanyResponse>> listFollowedCompanies(
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) Integer size,
            Authentication authentication
    ) {
        PagedResult<FollowedCompanyResponse> result = jobService.listFollowedCompanies(page, size, extractRequiredPrincipal(authentication));
        return ResponseEntity.ok().headers(buildPagingHeaders(result)).body(result.content());
    }

    @GetMapping("/dashboard/me")
    public JobDashboardResponse getMyDashboard(Authentication authentication) {
        return jobService.getMyDashboard(extractRequiredPrincipal(authentication));
    }

    @GetMapping("/search/suggestions")
    public List<JobSearchSuggestionResponse> suggestJobs(
            @RequestParam(name = "q") String query,
            @RequestParam(defaultValue = "8") @Min(1) Integer limit
    ) {
        return jobService.suggestJobs(query, limit);
    }

    @GetMapping("/companies/search")
    public List<CompanySearchResponse> searchCompanies(
            @RequestParam(name = "q") String query,
            @RequestParam(defaultValue = "10") @Min(1) Integer limit
    ) {
        return jobService.searchCompanies(query, limit);
    }

    @GetMapping("/{jobId}")
    public JobResponse getJobById(@PathVariable @Min(1) Long jobId, Authentication authentication) {
        return jobService.getJobById(jobId, extractOptionalPrincipal(authentication));
    }

    @PostMapping("/{jobId}/save")
    public JobResponse saveJob(@PathVariable @Min(1) Long jobId, Authentication authentication) {
        return jobService.saveJob(jobId, extractRequiredPrincipal(authentication));
    }

    @DeleteMapping("/{jobId}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsaveJob(@PathVariable @Min(1) Long jobId, Authentication authentication) {
        jobService.unsaveJob(jobId, extractRequiredPrincipal(authentication));
    }

    @PostMapping("/{jobId}/follow-company")
    public FollowedCompanyResponse followCompany(@PathVariable @Min(1) Long jobId, Authentication authentication) {
        return jobService.followCompany(jobId, extractRequiredPrincipal(authentication));
    }

    @DeleteMapping("/{jobId}/follow-company")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollowCompany(@PathVariable @Min(1) Long jobId, Authentication authentication) {
        jobService.unfollowCompany(jobId, extractRequiredPrincipal(authentication));
    }

    @PatchMapping("/{jobId}/status")
    public JobResponse updateJobStatus(
            @PathVariable @Min(1) Long jobId,
            @Valid @RequestBody UpdateJobStatusRequest request,
            Authentication authentication
    ) {
        return jobService.updateJobStatus(jobId, request.status(), extractRequiredPrincipal(authentication));
    }

    @PatchMapping("/{jobId}/close")
    public JobResponse closeJob(@PathVariable @Min(1) Long jobId, Authentication authentication) {
        return jobService.closeJob(jobId, extractRequiredPrincipal(authentication));
    }

    @PatchMapping("/internal/{jobId}/status")
    public JobResponse updateJobStatusInternal(
            @PathVariable @Min(1) Long jobId,
            @Valid @RequestBody UpdateJobStatusRequest request,
            @RequestHeader(name = INTERNAL_API_KEY_HEADER, required = false) String providedApiKey
    ) {
        requireInternalApiKey(providedApiKey);
        return jobService.updateJobStatusInternal(jobId, request.status());
    }

    @PostMapping("/internal/search/reindex")
    public JobSearchReindexResponse reindexSearch(
            @RequestHeader(name = INTERNAL_API_KEY_HEADER, required = false) String providedApiKey
    ) {
        requireInternalApiKey(providedApiKey);
        return jobSearchAdminService.reindexAllJobs();
    }

    @PostMapping("/internal/search/reindex/{jobId}")
    public JobSearchReindexResponse reindexSingleJob(
            @PathVariable @Min(1) Long jobId,
            @RequestHeader(name = INTERNAL_API_KEY_HEADER, required = false) String providedApiKey
    ) {
        requireInternalApiKey(providedApiKey);
        return jobSearchAdminService.reindexJob(jobId);
    }

    @PostMapping("/internal/search/reindex/companies")
    public JobSearchReindexResponse reindexCompanies(
            @RequestHeader(name = INTERNAL_API_KEY_HEADER, required = false) String providedApiKey
    ) {
        requireInternalApiKey(providedApiKey);
        return jobSearchAdminService.reindexAllCompanies();
    }

    private void requireInternalApiKey(String providedApiKey) {
        if (providedApiKey == null || !providedApiKey.equals(internalApiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal API key");
        }
    }

    private JwtUserPrincipal extractRequiredPrincipal(Authentication authentication) {
        JwtUserPrincipal principal = extractOptionalPrincipal(authentication);
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal;
    }

    private JwtUserPrincipal extractOptionalPrincipal(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof JwtUserPrincipal jwtPrincipal ? jwtPrincipal : null;
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
