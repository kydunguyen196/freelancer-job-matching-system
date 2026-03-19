package com.skillbridge.job_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.skillbridge.job_service.config.SearchProperties;
import com.skillbridge.job_service.domain.Job;
import com.skillbridge.job_service.dto.PagedResult;

@Service
@Primary
public class RoutingJobSearchService implements JobSearchService {

    private static final Logger log = LoggerFactory.getLogger(RoutingJobSearchService.class);

    private final SearchProperties searchProperties;
    private final DbJobSearchService dbJobSearchService;
    private final OpenSearchJobSearchService openSearchJobSearchService;

    public RoutingJobSearchService(
            SearchProperties searchProperties,
            DbJobSearchService dbJobSearchService,
            OpenSearchJobSearchService openSearchJobSearchService
    ) {
        this.searchProperties = searchProperties;
        this.dbJobSearchService = dbJobSearchService;
        this.openSearchJobSearchService = openSearchJobSearchService;
    }

    @Override
    public PagedResult<JobSearchResultItem> search(JobSearchRequest request) {
        if (!shouldUseOpenSearch()) {
            return dbJobSearchService.search(request);
        }
        try {
            return openSearchJobSearchService.search(request);
        } catch (RuntimeException ex) {
            log.warn("OpenSearch search failed, falling back to DB search: {}", ex.getMessage());
            return dbJobSearchService.search(request);
        }
    }

    @Override
    public java.util.List<JobSearchSuggestionItem> suggest(String query, int limit) {
        if (!shouldUseOpenSearch()) {
            return dbJobSearchService.suggest(query, limit);
        }
        try {
            return openSearchJobSearchService.suggest(query, limit);
        } catch (RuntimeException ex) {
            log.warn("OpenSearch suggest failed, falling back to DB search suggestions: {}", ex.getMessage());
            return dbJobSearchService.suggest(query, limit);
        }
    }

    @Override
    public java.util.List<CompanySearchResultItem> searchCompanies(String query, int limit) {
        if (!shouldUseOpenSearch()) {
            return dbJobSearchService.searchCompanies(query, limit);
        }
        try {
            return openSearchJobSearchService.searchCompanies(query, limit);
        } catch (RuntimeException ex) {
            log.warn("OpenSearch company search failed, falling back to DB company search: {}", ex.getMessage());
            return dbJobSearchService.searchCompanies(query, limit);
        }
    }

    @Override
    public boolean indexJob(Job job) {
        if (!shouldUseOpenSearch()) {
            return false;
        }
        return openSearchJobSearchService.indexJob(job);
    }

    @Override
    public boolean indexCompany(Long clientId) {
        if (!shouldUseOpenSearch()) {
            return false;
        }
        return openSearchJobSearchService.indexCompany(clientId);
    }

    @Override
    public boolean deleteJob(Long jobId) {
        if (!shouldUseOpenSearch()) {
            return false;
        }
        return openSearchJobSearchService.deleteJob(jobId);
    }

    @Override
    public boolean deleteCompany(Long clientId) {
        if (!shouldUseOpenSearch()) {
            return false;
        }
        return openSearchJobSearchService.deleteCompany(clientId);
    }

    @Override
    public boolean supportsIndexing() {
        return shouldUseOpenSearch() && openSearchJobSearchService.supportsIndexing();
    }

    @Override
    public String providerName() {
        return supportsIndexing() ? openSearchJobSearchService.providerName() : dbJobSearchService.providerName();
    }

    private boolean shouldUseOpenSearch() {
        return searchProperties.isEnabled()
                && "opensearch".equalsIgnoreCase(searchProperties.getProvider())
                && openSearchJobSearchService.supportsIndexing();
    }
}
