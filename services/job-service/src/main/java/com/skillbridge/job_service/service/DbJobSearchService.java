package com.skillbridge.job_service.service;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.skillbridge.job_service.domain.Job;
import com.skillbridge.job_service.dto.PagedResult;
import com.skillbridge.job_service.repository.JobRepository;

@Service
public class DbJobSearchService implements JobSearchService {

    private final JobRepository jobRepository;

    public DbJobSearchService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Override
    public PagedResult<JobSearchResultItem> search(JobSearchRequest request) {
        Specification<Job> spec = Specification.where(null);
        if (request.keyword() != null) {
            spec = spec.and(keywordContains(request.keyword()));
        }
        if (request.status() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), request.status()));
        }
        if (request.budgetMin() != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("budgetMax"), request.budgetMin()));
        }
        if (request.budgetMax() != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("budgetMin"), request.budgetMax()));
        }
        if (request.clientId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("clientId"), request.clientId()));
        }
        if (request.location() != null) {
            spec = spec.and(textContains("location", request.location()));
        }
        if (request.companyName() != null) {
            spec = spec.and(textContains("companyName", request.companyName()));
        }
        if (request.employmentType() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("employmentType"), request.employmentType()));
        }
        if (request.remote() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("remote"), request.remote()));
        }
        if (request.experienceYearsMin() != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("experienceYears"), request.experienceYearsMin()));
        }
        if (request.experienceYearsMax() != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("experienceYears"), request.experienceYearsMax()));
        }
        for (String tag : request.tags() == null ? List.<String>of() : request.tags()) {
            spec = spec.and((root, query, cb) -> cb.isMember(tag, root.get("tags")));
        }

        Pageable pageable = PageRequest.of(request.page(), request.size(), resolveSort(request.sort()));
        Page<Job> result = jobRepository.findAll(spec, pageable);
        List<JobSearchResultItem> content = result.getContent().stream()
                .map(JobSearchResultItem::fromJob)
                .toList();
        return new PagedResult<>(
                content,
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize()
        );
    }

    @Override
    public boolean indexJob(Job job) {
        return false;
    }

    @Override
    public boolean deleteJob(Long jobId) {
        return false;
    }

    @Override
    public boolean supportsIndexing() {
        return false;
    }

    @Override
    public String providerName() {
        return "db";
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

    private Sort resolveSort(JobSearchSort sort) {
        if (sort == null || sort == JobSearchSort.LATEST) {
            return Sort.by(Sort.Order.desc("createdAt"));
        }
        if (sort == JobSearchSort.SALARY_HIGH) {
            return Sort.by(Sort.Order.desc("budgetMax"), Sort.Order.desc("createdAt"));
        }
        return Sort.by(Sort.Order.asc("budgetMin"), Sort.Order.desc("createdAt"));
    }
}
