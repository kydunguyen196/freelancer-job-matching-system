package com.skillbridge.job_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.skillbridge.job_service.config.SearchProperties;
import com.skillbridge.job_service.dto.PagedResult;

@ExtendWith(MockitoExtension.class)
class RoutingJobSearchServiceTest {

    @Mock
    private DbJobSearchService dbJobSearchService;

    @Mock
    private OpenSearchJobSearchService openSearchJobSearchService;

    @Test
    void searchShouldFallBackToDbWhenOpenSearchFails() {
        SearchProperties properties = properties(true, "opensearch");
        RoutingJobSearchService routingJobSearchService = new RoutingJobSearchService(properties, dbJobSearchService, openSearchJobSearchService);
        JobSearchRequest request = new JobSearchRequest(null, null, null, null, null, List.of(), null, null, null, null, null, null, JobSearchSort.LATEST, 0, 20);
        PagedResult<JobSearchResultItem> dbResult = new PagedResult<>(List.of(), 0, 0, 0, 20);

        when(openSearchJobSearchService.supportsIndexing()).thenReturn(true);
        when(openSearchJobSearchService.search(request)).thenThrow(new IllegalStateException("cluster unavailable"));
        when(dbJobSearchService.search(request)).thenReturn(dbResult);

        PagedResult<JobSearchResultItem> result = routingJobSearchService.search(request);

        assertThat(result).isEqualTo(dbResult);
        verify(openSearchJobSearchService).search(request);
        verify(dbJobSearchService).search(request);
    }

    @Test
    void searchShouldUseDbWhenAdvancedSearchDisabled() {
        SearchProperties properties = properties(false, "opensearch");
        RoutingJobSearchService routingJobSearchService = new RoutingJobSearchService(properties, dbJobSearchService, openSearchJobSearchService);
        JobSearchRequest request = new JobSearchRequest(null, null, null, null, null, List.of(), null, null, null, null, null, null, JobSearchSort.LATEST, 0, 20);
        PagedResult<JobSearchResultItem> dbResult = new PagedResult<>(List.of(), 0, 0, 0, 20);

        when(dbJobSearchService.search(request)).thenReturn(dbResult);

        PagedResult<JobSearchResultItem> result = routingJobSearchService.search(request);

        assertThat(result).isEqualTo(dbResult);
        verify(dbJobSearchService).search(request);
        verifyNoInteractions(openSearchJobSearchService);
    }

    private SearchProperties properties(boolean enabled, String provider) {
        SearchProperties properties = new SearchProperties();
        properties.setEnabled(enabled);
        properties.setProvider(provider);
        return properties;
    }
}
