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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.job_service.domain.EmploymentType;
import com.skillbridge.job_service.domain.FollowedCompany;
import com.skillbridge.job_service.domain.Job;
import com.skillbridge.job_service.domain.JobStatus;
import com.skillbridge.job_service.domain.SavedJob;
import com.skillbridge.job_service.dto.CreateJobRequest;
import com.skillbridge.job_service.dto.FollowedCompanyResponse;
import com.skillbridge.job_service.dto.JobDashboardResponse;
import com.skillbridge.job_service.dto.JobResponse;
import com.skillbridge.job_service.dto.PagedResult;
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
    private final RestClient notificationRestClient;
    private final String internalApiKey;

    public JobService(
            JobRepository jobRepository,
            SavedJobRepository savedJobRepository,
            FollowedCompanyRepository followedCompanyRepository,
            @Value("${app.services.notification-base-url:http://localhost:8086}") String notificationBaseUrl,
            @Value("${app.internal.api-key}") String internalApiKey
    ) {
        this.jobRepository = jobRepository;
        this.savedJobRepository = savedJobRepository;
        this.followedCompanyRepository = followedCompanyRepository;
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
        job.setCompanyName(normalizeText(request.companyName()));
        job.setLocation(normalizeText(request.location()));
        job.setBudgetMin(request.budgetMin());
        job.setBudgetMax(request.budgetMax());
        job.setTags(normalizeTags(request.tags()));
        job.setEmploymentType(request.employmentType() == null ? EmploymentType.CONTRACT : request.employmentType());
        job.setRemote(Boolean.TRUE.equals(request.remote()));
        job.setExperienceYears(request.experienceYears());
        job.setStatus(resolveCreateStatus(request.status()));
        job.setExpiresAt(request.expiresAt());
        job.setClientId(principal.userId());
        applyStatusMetadata(job, job.getStatus(), Instant.now());

        Job savedJob = jobRepository.save(job);
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
        String normalizedKeyword = normalizeText(keyword);
        String normalizedLocation = normalizeText(location);
        String normalizedCompanyName = normalizeText(companyName);

        Specification<Job> spec = Specification.where(null);
        if (normalizedKeyword != null) {
            spec = spec.and(keywordContains(normalizedKeyword));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (budgetMin != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("budgetMax"), budgetMin));
        }
        if (budgetMax != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("budgetMin"), budgetMax));
        }
        if (clientId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("clientId"), clientId));
        }
        if (normalizedLocation != null) {
            spec = spec.and(textContains("location", normalizedLocation));
        }
        if (normalizedCompanyName != null) {
            spec = spec.and(textContains("companyName", normalizedCompanyName));
        }
        if (employmentType != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("employmentType"), employmentType));
        }
        if (remote != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("remote"), remote));
        }
        if (experienceYearsMin != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("experienceYears"), experienceYearsMin));
        }
        if (experienceYearsMax != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("experienceYears"), experienceYearsMax));
        }
        for (String tag : normalizedTags) {
            spec = spec.and((root, query, cb) -> cb.isMember(tag, root.get("tags")));
        }

        Pageable pageable = PageRequest.of(page, size, resolveSort(sortBy));
        Page<Job> result = jobRepository.findAll(spec, pageable);
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
    public JobResponse updateJobStatus(Long jobId, JobStatus status, JwtUserPrincipal principal) {
        ensureClientRole(principal);
        Job job = findJob(jobId);
        ensureOwner(job, principal.userId());

        if (status == JobStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IN_PROGRESS is reserved for internal workflow");
        }

        applyStatusMetadata(job, requireStatus(status), Instant.now());
        Job savedJob = jobRepository.save(job);
        notifyUsersForStatusChange(savedJob);
        return toResponse(savedJob, principal);
    }

    @Transactional
    public JobResponse updateJobStatusInternal(Long jobId, JobStatus status) {
        Job job = findJob(jobId);
        applyStatusMetadata(job, requireStatus(status), Instant.now());
        Job savedJob = jobRepository.save(job);
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

    private Job findJob(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
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

    private Specification<Job> keywordContains(String keyword) {
        String pattern = "%" + keyword.toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(root.get("description")), pattern),
                cb.like(cb.lower(root.get("location")), pattern),
                cb.like(cb.lower(root.get("companyName")), pattern)
        );
    }

    private Specification<Job> textContains(String fieldName, String value) {
        String pattern = "%" + value.toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get(fieldName)), pattern);
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

    private JobStatus resolveCreateStatus(JobStatus requestedStatus) {
        if (requestedStatus == null) {
            return JobStatus.OPEN;
        }
        if (requestedStatus == JobStatus.IN_PROGRESS || requestedStatus == JobStatus.EXPIRED || requestedStatus == JobStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only DRAFT or OPEN can be used when creating a job");
        }
        return requestedStatus;
    }

    private Sort resolveSort(String sortBy) {
        String normalizedSort = normalizeText(sortBy);
        if (normalizedSort == null || "latest".equalsIgnoreCase(normalizedSort) || "newest".equalsIgnoreCase(normalizedSort)) {
            return Sort.by(Sort.Order.desc("createdAt"));
        }
        if ("salary_high".equalsIgnoreCase(normalizedSort) || "salaryHigh".equalsIgnoreCase(normalizedSort)) {
            return Sort.by(Sort.Order.desc("budgetMax"), Sort.Order.desc("createdAt"));
        }
        if ("salary_low".equalsIgnoreCase(normalizedSort) || "salaryLow".equalsIgnoreCase(normalizedSort)) {
            return Sort.by(Sort.Order.asc("budgetMin"), Sort.Order.desc("createdAt"));
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

    private JobResponse toResponse(Job job, JwtUserPrincipal principal) {
        boolean saved = isFreelancer(principal) && savedJobRepository.existsByUserIdAndJobId(principal.userId(), job.getId());
        boolean followed = isFreelancer(principal) && followedCompanyRepository.existsByFollowerUserIdAndClientId(principal.userId(), job.getClientId());
        return toResponse(job, saved, followed);
    }

    private JobResponse toResponse(Job job, boolean savedByCurrentUser, boolean companyFollowedByCurrentUser) {
        return new JobResponse(
                job.getId(),
                job.getTitle(),
                job.getDescription(),
                job.getBudgetMin(),
                job.getBudgetMax(),
                List.copyOf(job.getTags()),
                job.getStatus().name(),
                job.getClientId(),
                job.getCompanyName(),
                job.getLocation(),
                job.getEmploymentType().name(),
                job.isRemote(),
                job.getExperienceYears(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getPublishedAt(),
                job.getExpiresAt(),
                job.getClosedAt(),
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
