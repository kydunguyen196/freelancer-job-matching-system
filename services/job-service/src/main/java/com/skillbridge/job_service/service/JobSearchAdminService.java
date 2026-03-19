package com.skillbridge.job_service.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.job_service.domain.Job;
import com.skillbridge.job_service.dto.JobSearchReindexResponse;
import com.skillbridge.job_service.repository.JobRepository;

@Service
public class JobSearchAdminService {

    private final JobRepository jobRepository;
    private final JobSearchService jobSearchService;

    public JobSearchAdminService(JobRepository jobRepository, JobSearchService jobSearchService) {
        this.jobRepository = jobRepository;
        this.jobSearchService = jobSearchService;
    }

    @Transactional(readOnly = true)
    public JobSearchReindexResponse reindexAllJobs() {
        if (!jobSearchService.supportsIndexing()) {
            return new JobSearchReindexResponse(
                    jobSearchService.providerName(),
                    0,
                    false,
                    "Advanced search indexing is disabled or unavailable"
            );
        }

        int indexedCount = 0;
        List<Job> jobs = jobRepository.findAll();
        for (Job job : jobs) {
            if (jobSearchService.indexJob(job)) {
                indexedCount++;
            }
        }
        return new JobSearchReindexResponse(
                jobSearchService.providerName(),
                indexedCount,
                true,
                "Reindexed " + indexedCount + " job documents"
        );
    }

    @Transactional(readOnly = true)
    public JobSearchReindexResponse reindexJob(Long jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));

        if (!jobSearchService.supportsIndexing()) {
            return new JobSearchReindexResponse(
                    jobSearchService.providerName(),
                    0,
                    false,
                    "Advanced search indexing is disabled or unavailable"
            );
        }

        boolean indexed = jobSearchService.indexJob(job);
        return new JobSearchReindexResponse(
                jobSearchService.providerName(),
                indexed ? 1 : 0,
                true,
                indexed ? "Reindexed job #" + jobId : "Failed to reindex job #" + jobId
        );
    }

    @Transactional(readOnly = true)
    public JobSearchReindexResponse reindexAllCompanies() {
        if (!jobSearchService.supportsIndexing()) {
            return new JobSearchReindexResponse(
                    jobSearchService.providerName(),
                    0,
                    false,
                    "Advanced search indexing is disabled or unavailable"
            );
        }

        int indexedCount = 0;
        List<Long> clientIds = jobRepository.findAll().stream()
                .map(Job::getClientId)
                .distinct()
                .collect(Collectors.toList());

        for (Long clientId : clientIds) {
            if (jobSearchService.indexCompany(clientId)) {
                indexedCount++;
            }
        }
        return new JobSearchReindexResponse(
                jobSearchService.providerName(),
                indexedCount,
                true,
                "Reindexed " + indexedCount + " company documents"
        );
    }
}
