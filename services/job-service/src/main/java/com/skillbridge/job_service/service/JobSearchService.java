package com.skillbridge.job_service.service;

import com.skillbridge.job_service.domain.Job;
import com.skillbridge.job_service.dto.PagedResult;

public interface JobSearchService {

    PagedResult<JobSearchResultItem> search(JobSearchRequest request);

    java.util.List<JobSearchSuggestionItem> suggest(String query, int limit);

    java.util.List<CompanySearchResultItem> searchCompanies(String query, int limit);

    boolean indexJob(Job job);

    boolean indexCompany(Long clientId);

    boolean deleteJob(Long jobId);

    boolean deleteCompany(Long clientId);

    boolean supportsIndexing();

    String providerName();
}
