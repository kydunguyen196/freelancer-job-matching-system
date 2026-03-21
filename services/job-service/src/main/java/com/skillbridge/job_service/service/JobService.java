package com.skillbridge.job_service.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.job_service.domain.EmploymentType;
import com.skillbridge.job_service.domain.FollowedCompany;
import com.skillbridge.job_service.domain.Job;
import com.skillbridge.job_service.domain.JobVisibility;
import com.skillbridge.job_service.domain.JobStatus;
import com.skillbridge.job_service.domain.SavedJob;
import com.skillbridge.job_service.domain.WorkMode;
import com.skillbridge.job_service.dto.CreateJobRequest;
import com.skillbridge.job_service.dto.CompanySearchResponse;
import com.skillbridge.job_service.dto.FollowedCompanyResponse;
import com.skillbridge.job_service.dto.JobDashboardResponse;
import com.skillbridge.job_service.dto.PatchJobRequest;
import com.skillbridge.job_service.dto.JobResponse;
import com.skillbridge.job_service.dto.JobSearchSuggestionResponse;
import com.skillbridge.job_service.dto.PagedResult;
import com.skillbridge.job_service.dto.UpdateJobRequest;
import com.skillbridge.job_service.repository.FollowedCompanyRepository;
import com.skillbridge.job_service.repository.JobRepository;
import com.skillbridge.job_service.repository.SavedJobRepository;
import com.skillbridge.job_service.security.JwtUserPrincipal;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);
    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final JobRepository jobRepository;
    private final SavedJobRepository savedJobRepository;
    private final FollowedCompanyRepository followedCompanyRepository;
    private final JobSearchService jobSearchService;
    private final RestClient notificationRestClient;
    private final String internalApiKey;

    public JobService(
            JobRepository jobRepository,
            SavedJobRepository savedJobRepository,
            FollowedCompanyRepository followedCompanyRepository,
            JobSearchService jobSearchService,
            @Value("${app.services.notification-base-url:http://localhost:8086}") String notificationBaseUrl,
            @Value("${app.internal.api-key}") String internalApiKey
    ) {
        this.jobRepository = jobRepository;
        this.savedJobRepository = savedJobRepository;
        this.followedCompanyRepository = followedCompanyRepository;
        this.jobSearchService = jobSearchService;
        this.notificationRestClient = notificationBaseUrl == null
                ? RestClient.builder().build()
                : RestClient.builder().baseUrl(notificationBaseUrl).build();
        this.internalApiKey = internalApiKey == null ? "" : internalApiKey;
    }

    @Transactional
    public JobResponse createJob(CreateJobRequest request, JwtUserPrincipal principal) {
        ensureClientRole(principal);
        validateBudgetRange(request.budgetMin(), request.budgetMax());
        validateExperienceYears(request.experienceYears());
        validateExpiration(request.expiresAt());

        Job job = new Job();
        job.setTitle(normalizeRequiredText(request.title(), "title"));
        job.setDescription(normalizeRequiredText(request.description(), "description"));
        job.setRequirements(normalizeOptionalText(request.requirements(), 6000));
        job.setResponsibilities(normalizeOptionalText(request.responsibilities(), 6000));
        job.setBenefits(normalizeOptionalText(request.benefits(), 4000));
        job.setCompanyName(normalizeText(request.companyName()));
        job.setLocation(normalizeText(request.location()));
        job.setBudgetMin(request.budgetMin());
        job.setBudgetMax(request.budgetMax());
        job.setTags(normalizeTags(request.tags()));
        job.setEmploymentType(request.employmentType() == null ? EmploymentType.CONTRACT : request.employmentType());
        job.setWorkMode(resolveWorkMode(request.workMode(), request.remote()));
        job.setRemote(resolveRemoteFlag(job.getWorkMode(), request.remote()));
        job.setExperienceYears(request.experienceYears());
        job.setCategory(normalizeOptionalText(request.category(), 120));
        job.setVisibility(resolveVisibility(request.visibility()));
        job.setOpenings(resolveOpenings(request.openings()));
        job.setStatus(resolveCreateStatus(request.status()));
        job.setExpiresAt(request.expiresAt());
        job.setClientId(principal.userId());
        applyStatusMetadata(job, job.getStatus(), Instant.now());

        Job savedJob = jobRepository.save(job);
        safeIndexJob(savedJob);
        notifyFollowersForPublishedJob(savedJob);
        return toResponse(savedJob, principal);
    }

    @Transactional(readOnly = true)
    public PagedResult<JobResponse> listJobs(
            String keyword,
            JobStatus status,
            BigDecimal budgetMin,
            BigDecimal budgetMax,
            Long clientId,
            List<String> tags,
            String location,
            String companyName,
            EmploymentType employmentType,
            Boolean remote,
            Integer experienceYearsMin,
            Integer experienceYearsMax,
            String sortBy,
            int page,
            int size,
            JwtUserPrincipal principal
    ) {
        validateBudgetRange(budgetMin, budgetMax);
        validateExperienceRange(experienceYearsMin, experienceYearsMax);
        validatePaging(page, size);
        List<String> normalizedTags = normalizeTags(tags);
        JobSearchRequest request = new JobSearchRequest(
                normalizeText(keyword),
                status,
                budgetMin,
                budgetMax,
                clientId,
                normalizedTags,
                normalizeText(location),
                normalizeText(companyName),
                employmentType,
                remote,
                experienceYearsMin,
                experienceYearsMax,
                resolveSort(sortBy),
                page,
                size
        );
        PagedResult<JobSearchResultItem> result = jobSearchService.search(request);
        return toPagedResult(result, principal);
    }

    @Transactional(readOnly = true)
    public PagedResult<JobResponse> listMyJobs(
            JobStatus status,
            String sortBy,
            int page,
            int size,
            JwtUserPrincipal principal
    ) {
        ensureClientRole(principal);
        return listJobs(
                null,
                status,
                null,
                null,
                principal.userId(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                sortBy,
                page,
                size,
                principal
        );
    }

    @Transactional(readOnly = true)
    public JobResponse getJobById(Long jobId, JwtUserPrincipal principal) {
        return toResponse(findJob(jobId), principal);
    }

    @Transactional
    public JobResponse updateJob(Long jobId, UpdateJobRequest request, JwtUserPrincipal principal) {
        ensureClientRole(principal);
        Job job = findJob(jobId);
        ensureOwner(job, principal.userId());
        ensureJobEditable(job);

        validateBudgetRange(request.budgetMin(), request.budgetMax());
        validateExperienceYears(request.experienceYears());
        validateExpiration(request.expiresAt());
        validateOpenings(request.openings());

        job.setTitle(normalizeRequiredText(request.title(), "title"));
        job.setDescription(normalizeRequiredText(request.description(), "description"));
        job.setRequirements(normalizeOptionalText(request.requirements(), 6000));
        job.setResponsibilities(normalizeOptionalText(request.responsibilities(), 6000));
        job.setBenefits(normalizeOptionalText(request.benefits(), 4000));
        job.setBudgetMin(request.budgetMin());
        job.setBudgetMax(request.budgetMax());
        job.setTags(normalizeTags(request.tags()));
        job.setCompanyName(normalizeText(request.companyName()));
        job.setLocation(normalizeText(request.location()));
        job.setEmploymentType(request.employmentType() == null ? EmploymentType.CONTRACT : request.employmentType());
        job.setWorkMode(resolveWorkMode(request.workMode(), request.remote()));
        job.setRemote(resolveRemoteFlag(job.getWorkMode(), request.remote()));
        job.setExperienceYears(request.experienceYears());
        job.setCategory(normalizeOptionalText(request.category(), 120));
        job.setVisibility(resolveVisibility(request.visibility()));
        job.setOpenings(resolveOpenings(request.openings()));
        job.setExpiresAt(request.expiresAt());

        Job savedJob = jobRepository.save(job);
        safeIndexJob(savedJob);
        return toResponse(savedJob, principal);
    }

    @Transactional
    public JobResponse patchJob(Long jobId, PatchJobRequest request, JwtUserPrincipal principal) {
        ensureClientRole(principal);
        Job job = findJob(jobId);
        ensureOwner(job, principal.userId());
        ensureJobEditable(job);

        BigDecimal nextBudgetMin = request.budgetMin() != null ? request.budgetMin() : job.getBudgetMin();
        BigDecimal nextBudgetMax = request.budgetMax() != null ? request.budgetMax() : job.getBudgetMax();
        validateBudgetRange(nextBudgetMin, nextBudgetMax);
        validateExperienceYears(request.experienceYears());
        validateExpiration(request.expiresAt());
        validateOpenings(request.openings());

        if (request.title() != null) {
            job.setTitle(normalizeRequiredText(request.title(), "title"));
        }
        if (request.description() != null) {
            job.setDescription(normalizeRequiredText(request.description(), "description"));
        }
        if (request.requirements() != null) {
            job.setRequirements(normalizeOptionalText(request.requirements(), 6000));
        }
        if (request.responsibilities() != null) {
            job.setResponsibilities(normalizeOptionalText(request.responsibilities(), 6000));
        }
        if (request.benefits() != null) {
            job.setBenefits(normalizeOptionalText(request.benefits(), 4000));
        }
        if (request.budgetMin() != null) {
            job.setBudgetMin(request.budgetMin());
        }
        if (request.budgetMax() != null) {
            job.setBudgetMax(request.budgetMax());
        }
        if (request.tags() != null) {
            job.setTags(normalizeTags(request.tags()));
        }
        if (request.companyName() != null) {
            job.setCompanyName(normalizeText(request.companyName()));
        }
        if (request.location() != null) {
            job.setLocation(normalizeText(request.location()));
        }
        if (request.employmentType() != null) {
            job.setEmploymentType(request.employmentType());
        }
        if (request.workMode() != null || request.remote() != null) {
            WorkMode nextWorkMode = resolveWorkMode(
                    request.workMode() != null ? request.workMode() : job.getWorkMode(),
                    request.remote()
            );
            job.setWorkMode(nextWorkMode);
            job.setRemote(resolveRemoteFlag(nextWorkMode, request.remote()));
        }
        if (request.experienceYears() != null) {
            job.setExperienceYears(request.experienceYears());
        }
        if (request.category() != null) {
            job.setCategory(normalizeOptionalText(request.category(), 120));
        }
        if (request.visibility() != null) {
            job.setVisibility(request.visibility());
        }
        if (request.openings() != null) {
            job.setOpenings(request.openings());
        }
        if (request.expiresAt() != null) {
            job.setExpiresAt(request.expiresAt());
        }

        Job savedJob = jobRepository.save(job);
        safeIndexJob(savedJob);
        return toResponse(savedJob, principal);
    }

    @Transactional
    public JobResponse updateJobStatus(Long jobId, JobStatus status, JwtUserPrincipal principal) {
        ensureClientRole(principal);
        Job job = findJob(jobId);
        ensureOwner(job, principal.userId());

        if (status == JobStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IN_PROGRESS is reserved for internal workflow");
        }

        applyStatusMetadata(job, requireStatus(status), Instant.now());
        Job savedJob = jobRepository.save(job);
        safeIndexJob(savedJob);
        notifyUsersForStatusChange(savedJob);
        return toResponse(savedJob, principal);
    }

    @Transactional
    public JobResponse updateJobStatusInternal(Long jobId, JobStatus status) {
        Job job = findJob(jobId);
        applyStatusMetadata(job, requireStatus(status), Instant.now());
        Job savedJob = jobRepository.save(job);
        safeIndexJob(savedJob);
        notifyUsersForStatusChange(savedJob);
        return toResponse(savedJob, null);
    }

    @Transactional
    public JobResponse closeJob(Long jobId, JwtUserPrincipal principal) {
        return updateJobStatus(jobId, JobStatus.CLOSED, principal);
    }

    @Transactional
    public JobResponse saveJob(Long jobId, JwtUserPrincipal principal) {
        ensureFreelancerRole(principal);
        Job job = findJob(jobId);
        if (!savedJobRepository.existsByUserIdAndJobId(principal.userId(), jobId)) {
            SavedJob savedJob = new SavedJob();
            savedJob.setUserId(principal.userId());
            savedJob.setJobId(jobId);
            savedJob.setJobOwnerClientId(job.getClientId());
            savedJobRepository.save(savedJob);
        }
        return toResponse(job, principal);
    }

    @Transactional
    public void unsaveJob(Long jobId, JwtUserPrincipal principal) {
        ensureFreelancerRole(principal);
        savedJobRepository.deleteByUserIdAndJobId(principal.userId(), jobId);
    }

    @Transactional(readOnly = true)
    public PagedResult<JobResponse> listSavedJobs(int page, int size, JwtUserPrincipal principal) {
        ensureFreelancerRole(principal);
        validatePaging(page, size);
        Page<SavedJob> savedJobs = savedJobRepository.findByUserIdOrderByCreatedAtDesc(
                principal.userId(),
                PageRequest.of(page, size)
        );
        List<Long> jobIds = savedJobs.getContent().stream()
                .map(SavedJob::getJobId)
                .toList();
        List<Job> jobs = jobRepository.findAllById(jobIds);
        List<JobResponse> responses = jobs.stream()
                .map(job -> toResponse(job, principal))
                .sorted((left, right) -> Integer.compare(jobIds.indexOf(left.id()), jobIds.indexOf(right.id())))
                .toList();
        return new PagedResult<>(
                responses,
                savedJobs.getTotalElements(),
                savedJobs.getTotalPages(),
                savedJobs.getNumber(),
                savedJobs.getSize()
        );
    }

    @Transactional
    public FollowedCompanyResponse followCompany(Long jobId, JwtUserPrincipal principal) {
        ensureFreelancerRole(principal);
        Job job = findJob(jobId);
        Long clientId = job.getClientId();
        Instant now = Instant.now();
        if (!followedCompanyRepository.existsByFollowerUserIdAndClientId(principal.userId(), clientId)) {
            FollowedCompany followedCompany = new FollowedCompany();
            followedCompany.setFollowerUserId(principal.userId());
            followedCompany.setClientId(clientId);
            followedCompany.setCompanyName(resolveCompanyName(job));
            followedCompanyRepository.save(followedCompany);
        }
        return new FollowedCompanyResponse(clientId, resolveCompanyName(job), now);
    }

    @Transactional
    public void unfollowCompany(Long jobId, JwtUserPrincipal principal) {
        ensureFreelancerRole(principal);
        Job job = findJob(jobId);
        followedCompanyRepository.deleteByFollowerUserIdAndClientId(principal.userId(), job.getClientId());
    }

    @Transactional(readOnly = true)
    public PagedResult<FollowedCompanyResponse> listFollowedCompanies(int page, int size, JwtUserPrincipal principal) {
        ensureFreelancerRole(principal);
        validatePaging(page, size);
        Page<FollowedCompany> result = followedCompanyRepository.findByFollowerUserIdOrderByCreatedAtDesc(
                principal.userId(),
                PageRequest.of(page, size)
        );
        List<FollowedCompanyResponse> content = result.getContent().stream()
                .map(company -> new FollowedCompanyResponse(
                        company.getClientId(),
                        company.getCompanyName(),
                        company.getCreatedAt()
                ))
                .toList();
        return new PagedResult<>(
                content,
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize()
        );
    }

    @Transactional(readOnly = true)
    public JobDashboardResponse getMyDashboard(JwtUserPrincipal principal) {
        ensureClientRole(principal);
        Long clientId = principal.userId();
        return new JobDashboardResponse(
                jobRepository.countByClientId(clientId),
                jobRepository.countByClientIdAndStatus(clientId, JobStatus.DRAFT),
                jobRepository.countByClientIdAndStatus(clientId, JobStatus.OPEN),
                jobRepository.countByClientIdAndStatus(clientId, JobStatus.IN_PROGRESS),
                jobRepository.countByClientIdAndStatus(clientId, JobStatus.CLOSED),
                jobRepository.countByClientIdAndStatus(clientId, JobStatus.EXPIRED),
                savedJobRepository.countByJobOwnerClientId(clientId),
                followedCompanyRepository.countByClientId(clientId)
        );
    }

    @Transactional(readOnly = true)
    public List<JobSearchSuggestionResponse> suggestJobs(String query, int limit) {
        validateSuggestionLimit(limit);
        return jobSearchService.suggest(normalizeText(query), limit).stream()
                .map(item -> new JobSearchSuggestionResponse(item.value(), item.type()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CompanySearchResponse> searchCompanies(String query, int limit) {
        validateSuggestionLimit(limit);
        return jobSearchService.searchCompanies(normalizeText(query), limit).stream()
                .map(item -> new CompanySearchResponse(
                        item.clientId(),
                        item.companyName(),
                        item.totalJobs(),
                        item.openJobs(),
                        item.latestJobCreatedAt(),
                        item.latestJobUpdatedAt(),
                        item.locations(),
                        item.employmentTypes(),
                        item.topTags()
                ))
                .toList();
    }

    private void ensureClientRole(JwtUserPrincipal principal) {
        ensureRole(principal, "CLIENT", "Only CLIENT can perform this action");
    }

    private void ensureFreelancerRole(JwtUserPrincipal principal) {
        ensureRole(principal, "FREELANCER", "Only FREELANCER can perform this action");
    }

    private void ensureRole(JwtUserPrincipal principal, String expectedRole, String message) {
        if (principal == null || principal.role() == null || !expectedRole.equalsIgnoreCase(principal.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }

    private void ensureOwner(Job job, Long clientId) {
        if (!Objects.equals(job.getClientId(), clientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only job owner can manage this job");
        }
    }

    private void ensureJobEditable(Job job) {
        if (job.getStatus() == JobStatus.IN_PROGRESS || job.getStatus() == JobStatus.CLOSED || job.getStatus() == JobStatus.EXPIRED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Job details can only be edited while status is DRAFT or OPEN"
            );
        }
    }

    private Job findJob(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
    }

    private PagedResult<JobResponse> toPagedResult(PagedResult<JobSearchResultItem> result, JwtUserPrincipal principal) {
        Set<Long> savedJobIds = resolveSavedJobIds(principal, result.content().stream().map(JobSearchResultItem::id).toList());
        Set<Long> followedCompanyIds = resolveFollowedCompanyIds(principal, result.content().stream().map(JobSearchResultItem::clientId).toList());
        List<JobResponse> content = result.content().stream()
                .map(job -> toResponse(job, savedJobIds.contains(job.id()), followedCompanyIds.contains(job.clientId())))
                .toList();
        return new PagedResult<>(
                content,
                result.totalElements(),
                result.totalPages(),
                result.page(),
                result.size()
        );
    }

    private PagedResult<JobResponse> toPagedResult(Page<Job> result, JwtUserPrincipal principal) {
        Set<Long> savedJobIds = resolveSavedJobIds(principal, result.getContent().stream().map(Job::getId).toList());
        Set<Long> followedCompanyIds = resolveFollowedCompanyIds(principal, result.getContent().stream().map(Job::getClientId).toList());
        List<JobResponse> content = result.getContent().stream()
                .map(job -> toResponse(job, savedJobIds.contains(job.getId()), followedCompanyIds.contains(job.getClientId())))
                .toList();
        return new PagedResult<>(
                content,
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize()
        );
    }

    private Set<Long> resolveSavedJobIds(JwtUserPrincipal principal, Collection<Long> jobIds) {
        if (!isFreelancer(principal) || jobIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(savedJobRepository.findSavedJobIds(principal.userId(), jobIds));
    }

    private Set<Long> resolveFollowedCompanyIds(JwtUserPrincipal principal, Collection<Long> clientIds) {
        if (!isFreelancer(principal) || clientIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(followedCompanyRepository.findFollowedClientIds(principal.userId(), clientIds));
    }

    private boolean isFreelancer(JwtUserPrincipal principal) {
        return principal != null && principal.role() != null && "FREELANCER".equalsIgnoreCase(principal.role());
    }

    private void validateBudgetRange(BigDecimal min, BigDecimal max) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "budgetMin must be less than or equal to budgetMax");
        }
    }

    private void validateExperienceRange(Integer min, Integer max) {
        validateExperienceYears(min);
        validateExperienceYears(max);
        if (min != null && max != null && min > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "experienceYearsMin must be less than or equal to experienceYearsMax");
        }
    }

    private void validateExperienceYears(Integer experienceYears) {
        if (experienceYears != null && experienceYears < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "experienceYears must be greater than or equal to 0");
        }
    }

    private void validateOpenings(Integer openings) {
        if (openings != null && openings < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "openings must be greater than or equal to 1");
        }
    }

    private void validateExpiration(Instant expiresAt) {
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expiresAt must be in the future");
        }
    }

    private void validatePaging(int page, int size) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and 100");
        }
    }

    private void validateSuggestionLimit(int limit) {
        if (limit < 1 || limit > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 20");
        }
    }

    private JobStatus resolveCreateStatus(JobStatus requestedStatus) {
        if (requestedStatus == null) {
            return JobStatus.OPEN;
        }
        if (requestedStatus == JobStatus.IN_PROGRESS || requestedStatus == JobStatus.EXPIRED || requestedStatus == JobStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only DRAFT or OPEN can be used when creating a job");
        }
        return requestedStatus;
    }

    private WorkMode resolveWorkMode(WorkMode requestedWorkMode, Boolean requestedRemote) {
        if (requestedWorkMode == null && requestedRemote == null) {
            return WorkMode.ONSITE;
        }
        if (requestedWorkMode == null) {
            return Boolean.TRUE.equals(requestedRemote) ? WorkMode.REMOTE : WorkMode.ONSITE;
        }
        if (requestedRemote != null) {
            boolean compatible = requestedWorkMode == WorkMode.HYBRID
                    || (requestedWorkMode == WorkMode.REMOTE && requestedRemote)
                    || (requestedWorkMode == WorkMode.ONSITE && !requestedRemote);
            if (!compatible) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "remote is not compatible with workMode");
            }
        }
        return requestedWorkMode;
    }

    private boolean resolveRemoteFlag(WorkMode workMode, Boolean requestedRemote) {
        if (workMode == WorkMode.HYBRID) {
            return true;
        }
        if (requestedRemote != null) {
            return requestedRemote;
        }
        return workMode == WorkMode.REMOTE;
    }

    private JobVisibility resolveVisibility(JobVisibility requestedVisibility) {
        return requestedVisibility == null ? JobVisibility.PUBLIC : requestedVisibility;
    }

    private Integer resolveOpenings(Integer openings) {
        if (openings == null) {
            return null;
        }
        validateOpenings(openings);
        return openings;
    }

    private JobSearchSort resolveSort(String sortBy) {
        String normalizedSort = normalizeText(sortBy);
        if (normalizedSort == null || "latest".equalsIgnoreCase(normalizedSort) || "newest".equalsIgnoreCase(normalizedSort)) {
            return JobSearchSort.LATEST;
        }
        if ("salary_high".equalsIgnoreCase(normalizedSort) || "salaryHigh".equalsIgnoreCase(normalizedSort)) {
            return JobSearchSort.SALARY_HIGH;
        }
        if ("salary_low".equalsIgnoreCase(normalizedSort) || "salaryLow".equalsIgnoreCase(normalizedSort)) {
            return JobSearchSort.SALARY_LOW;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sortBy value");
    }

    private JobStatus requireStatus(JobStatus status) {
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }
        return status;
    }

    private void applyStatusMetadata(Job job, JobStatus status, Instant now) {
        job.setStatus(status);
        switch (status) {
            case DRAFT -> {
                job.setPublishedAt(null);
                job.setClosedAt(null);
            }
            case OPEN -> {
                job.setPublishedAt(now);
                job.setClosedAt(null);
            }
            case IN_PROGRESS -> {
                if (job.getPublishedAt() == null) {
                    job.setPublishedAt(now);
                }
                job.setClosedAt(null);
            }
            case CLOSED -> job.setClosedAt(now);
            case EXPIRED -> {
                job.setExpiresAt(job.getExpiresAt() == null ? now : job.getExpiresAt());
                job.setClosedAt(now);
            }
        }
    }

    private void notifyFollowersForPublishedJob(Job job) {
        if (job.getStatus() != JobStatus.OPEN) {
            return;
        }
        String companyName = resolveCompanyName(job);
        for (Long followerUserId : followedCompanyRepository.findFollowerUserIdsByClientId(job.getClientId())) {
            safeCreateNotification(
                    followerUserId,
                    "JOB_PUBLISHED",
                    "New job from " + companyName,
                    companyName + " posted a new job: " + job.getTitle()
            );
        }
    }

    private void notifyUsersForStatusChange(Job job) {
        if (job.getStatus() == JobStatus.DRAFT) {
            return;
        }
        String message = "Job #" + job.getId() + " is now " + job.getStatus().name();
        for (Long savedUserId : savedJobRepository.findUserIdsByJobId(job.getId())) {
            safeCreateNotification(
                    savedUserId,
                    "JOB_STATUS_CHANGED",
                    "Saved job updated",
                    message
            );
        }
        if (job.getStatus() == JobStatus.OPEN) {
            notifyFollowersForPublishedJob(job);
        }
    }

    private void safeCreateNotification(Long recipientUserId, String type, String title, String message) {
        try {
            notificationRestClient.post()
                    .uri("/notifications/internal")
                    .header(INTERNAL_API_KEY_HEADER, internalApiKey)
                    .body(new CreateNotificationRequest(recipientUserId, type, title, message))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            log.warn("Failed to create notification type={} recipient={}: {}", type, recipientUserId, ex.getMessage());
        }
    }

    private String normalizeRequiredText(String text, String field) {
        String normalized = normalizeText(text);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must not be blank");
        }
        return normalized;
    }

    private String normalizeOptionalText(String text, int maxLength) {
        String normalized = normalizeText(text);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Field exceeds max length " + maxLength);
        }
        return normalized;
    }

    private String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return new ArrayList<>();
        }
        return tags.stream()
                .map(this::normalizeText)
                .filter(Objects::nonNull)
                .map(tag -> tag.toLowerCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private String resolveCompanyName(Job job) {
        return job.getCompanyName() == null ? "Client #" + job.getClientId() : job.getCompanyName();
    }

    private void safeIndexJob(Job job) {
        boolean indexedJob = jobSearchService.indexJob(job);
        boolean indexedCompany = job.getClientId() != null && jobSearchService.indexCompany(job.getClientId());
        if (!indexedJob && !indexedCompany) {
            log.debug("Search indexing skipped or failed for jobId={}", job.getId());
        }
    }

    private JobResponse toResponse(Job job, JwtUserPrincipal principal) {
        boolean saved = isFreelancer(principal) && savedJobRepository.existsByUserIdAndJobId(principal.userId(), job.getId());
        boolean followed = isFreelancer(principal) && followedCompanyRepository.existsByFollowerUserIdAndClientId(principal.userId(), job.getClientId());
        return toResponse(job, saved, followed);
    }

    private JobResponse toResponse(Job job, boolean savedByCurrentUser, boolean companyFollowedByCurrentUser) {
        return toResponse(JobSearchResultItem.fromJob(job), savedByCurrentUser, companyFollowedByCurrentUser);
    }

    private JobResponse toResponse(JobSearchResultItem job, boolean savedByCurrentUser, boolean companyFollowedByCurrentUser) {
        return new JobResponse(
                job.id(),
                job.title(),
                job.description(),
                job.requirements(),
                job.responsibilities(),
                job.benefits(),
                job.budgetMin(),
                job.budgetMax(),
                List.copyOf(job.tags()),
                job.status(),
                job.clientId(),
                job.companyName(),
                job.location(),
                job.employmentType(),
                job.workMode(),
                job.remote(),
                job.experienceYears(),
                job.category(),
                job.visibility(),
                job.openings(),
                job.createdAt(),
                job.updatedAt(),
                job.publishedAt(),
                job.expiresAt(),
                job.closedAt(),
                savedByCurrentUser,
                companyFollowedByCurrentUser
        );
    }

    private record CreateNotificationRequest(
            Long recipientUserId,
            String type,
            String title,
            String message
    ) {
    }
}
