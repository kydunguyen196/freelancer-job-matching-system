package com.skillbridge.job_service.service;

import com.skillbridge.job_service.domain.Job;
import com.skillbridge.job_service.dto.PagedResult;

public interface JobSearchService {

    PagedResult<JobSearchResultItem> search(JobSearchRequest request);

    boolean indexJob(Job job);

    boolean deleteJob(Long jobId);

    boolean supportsIndexing();

    String providerName();
}
