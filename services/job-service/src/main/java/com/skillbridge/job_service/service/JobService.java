package com.skillbridge.job_service.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.job_service.domain.Job;
import com.skillbridge.job_service.domain.JobStatus;
import com.skillbridge.job_service.dto.CreateJobRequest;
import com.skillbridge.job_service.dto.JobResponse;
import com.skillbridge.job_service.dto.PagedResult;
import com.skillbridge.job_service.repository.JobRepository;
import com.skillbridge.job_service.security.JwtUserPrincipal;

@Service
public class JobService {

    private final JobRepository jobRepository;

    public JobService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Transactional
    public JobResponse createJob(CreateJobRequest request, JwtUserPrincipal principal) {
        ensureClientRole(principal);
        validateBudgetRange(request.budgetMin(), request.budgetMax());

        Job job = new Job();
        job.setTitle(normalizeRequiredText(request.title(), "title"));
        job.setDescription(normalizeRequiredText(request.description(), "description"));
        job.setBudgetMin(request.budgetMin());
        job.setBudgetMax(request.budgetMax());
        job.setTags(normalizeTags(request.tags()));
        job.setStatus(JobStatus.OPEN);
        job.setClientId(principal.userId());

        return toResponse(jobRepository.save(job));
    }

    @Transactional(readOnly = true)
    public PagedResult<JobResponse> listJobs(
            String keyword,
            JobStatus status,
            BigDecimal budgetMin,
            BigDecimal budgetMax,
            Long clientId,
            List<String> tags,
            int page,
            int size
    ) {
        validateBudgetRange(budgetMin, budgetMax);
        validatePaging(page, size);
        List<String> normalizedTags = normalizeTags(tags);
        String normalizedKeyword = normalizeText(keyword);

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
        for (String tag : normalizedTags) {
            spec = spec.and((root, query, cb) -> cb.isMember(tag, root.get("tags")));
        }

        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Job> result = jobRepository.findAll(spec, pageable);

        List<JobResponse> content = result.getContent().stream()
                .map(this::toResponse)
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
    public JobResponse getJobById(Long jobId) {
        return toResponse(findJob(jobId));
    }

    @Transactional
    public JobResponse closeJob(Long jobId, JwtUserPrincipal principal) {
        ensureClientRole(principal);
        Job job = findJob(jobId);

        if (!Objects.equals(job.getClientId(), principal.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only job owner can close this job");
        }

        if (job.getStatus() != JobStatus.CLOSED) {
            job.setStatus(JobStatus.CLOSED);
            job.setClosedAt(Instant.now());
        }
        return toResponse(jobRepository.save(job));
    }

    private void ensureClientRole(JwtUserPrincipal principal) {
        if (principal == null || principal.role() == null || !"CLIENT".equalsIgnoreCase(principal.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only CLIENT can perform this action");
        }
    }

    private Job findJob(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
    }

    private Specification<Job> keywordContains(String keyword) {
        String pattern = "%" + keyword.toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(root.get("description")), pattern)
        );
    }

    private void validateBudgetRange(BigDecimal min, BigDecimal max) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "budgetMin must be less than or equal to budgetMax");
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
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private JobResponse toResponse(Job job) {
        return new JobResponse(
                job.getId(),
                job.getTitle(),
                job.getDescription(),
                job.getBudgetMin(),
                job.getBudgetMax(),
                List.copyOf(job.getTags()),
                job.getStatus().name(),
                job.getClientId(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getClosedAt()
        );
    }
}
