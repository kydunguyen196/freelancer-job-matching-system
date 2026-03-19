package com.skillbridge.job_service.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.skillbridge.job_service.config.SearchProperties;
import com.skillbridge.job_service.domain.Job;
import com.skillbridge.job_service.dto.PagedResult;

@Service
public class OpenSearchJobSearchService implements JobSearchService {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchJobSearchService.class);

    private final SearchProperties searchProperties;
    private final RestClient restClient;
    private volatile boolean jobIndexEnsured;

    public OpenSearchJobSearchService(SearchProperties searchProperties) {
        this.searchProperties = searchProperties;
        this.restClient = createRestClient(searchProperties);
    }

    @Override
    public PagedResult<JobSearchResultItem> search(JobSearchRequest request) {
        ensureJobIndex();
        JsonNode response = restClient.post()
                .uri("/{index}/_search", searchProperties.getOpensearch().getIndexJobs())
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildSearchRequest(request))
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException("OpenSearch search response was empty");
        }

        List<JobSearchResultItem> content = new ArrayList<>();
        JsonNode hitsNode = response.path("hits").path("hits");
        if (hitsNode.isArray()) {
            for (JsonNode hit : hitsNode) {
                JsonNode source = hit.path("_source");
                if (!source.isMissingNode() && !source.isNull()) {
                    content.add(toSearchResult(source));
                }
            }
        }

        long totalElements = extractTotalHits(response.path("hits").path("total"));
        int totalPages = request.size() == 0 ? 0 : (int) Math.ceil((double) totalElements / request.size());
        return new PagedResult<>(content, totalElements, totalPages, request.page(), request.size());
    }

    @Override
    public boolean indexJob(Job job) {
        if (!supportsIndexing()) {
            return false;
        }
        try {
            ensureJobIndex();
            restClient.put()
                    .uri("/{index}/_doc/{id}", searchProperties.getOpensearch().getIndexJobs(), job.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toIndexDocument(job))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException | IllegalStateException ex) {
            log.warn("Failed to index jobId={} into OpenSearch: {}", job.getId(), ex.getMessage());
            return false;
        }
    }

    @Override
    public boolean deleteJob(Long jobId) {
        if (!supportsIndexing()) {
            return false;
        }
        try {
            ensureJobIndex();
            restClient.delete()
                    .uri("/{index}/_doc/{id}", searchProperties.getOpensearch().getIndexJobs(), jobId)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound ex) {
            return false;
        } catch (RestClientException | IllegalStateException ex) {
            log.warn("Failed to delete jobId={} from OpenSearch: {}", jobId, ex.getMessage());
            return false;
        }
    }

    @Override
    public boolean supportsIndexing() {
        SearchProperties.OpenSearchProperties properties = searchProperties.getOpensearch();
        return searchProperties.isEnabled()
                && "opensearch".equalsIgnoreCase(searchProperties.getProvider())
                && restClient != null
                && notBlank(properties.getUrl())
                && notBlank(properties.getIndexJobs());
    }

    @Override
    public String providerName() {
        return "opensearch";
    }

    private RestClient createRestClient(SearchProperties properties) {
        if (!notBlank(properties.getOpensearch().getUrl())) {
            return null;
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getOpensearch().getConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getOpensearch().getSocketTimeoutMs());
        return RestClient.builder()
                .baseUrl(properties.getOpensearch().getUrl())
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> {
                    if (notBlank(properties.getOpensearch().getUsername())) {
                        headers.setBasicAuth(
                                properties.getOpensearch().getUsername(),
                                properties.getOpensearch().getPassword() == null ? "" : properties.getOpensearch().getPassword()
                        );
                    }
                })
                .build();
    }

    private void ensureJobIndex() {
        if (jobIndexEnsured) {
            return;
        }
        if (!supportsIndexing()) {
            throw new IllegalStateException("OpenSearch indexing is not configured");
        }
        synchronized (this) {
            if (jobIndexEnsured) {
                return;
            }
            if (!jobIndexExists()) {
                createJobIndex();
            }
            jobIndexEnsured = true;
        }
    }

    private boolean jobIndexExists() {
        try {
            restClient.get()
                    .uri("/{index}", searchProperties.getOpensearch().getIndexJobs())
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound ex) {
            return false;
        } catch (RestClientException ex) {
            throw new IllegalStateException("Could not check OpenSearch job index", ex);
        }
    }

    private void createJobIndex() {
        try {
            restClient.put()
                    .uri("/{index}", searchProperties.getOpensearch().getIndexJobs())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildJobIndexMapping())
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.BadRequest ex) {
            String responseBody = ex.getResponseBodyAsString();
            if (responseBody != null && responseBody.contains("resource_already_exists_exception")) {
                return;
            }
            throw ex;
        } catch (RestClientException ex) {
            throw new IllegalStateException("Could not create OpenSearch job index", ex);
        }
    }

    private Map<String, Object> buildJobIndexMapping() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", Map.of("type", "long"));
        properties.put("title", textWithKeyword());
        properties.put("description", Map.of("type", "text"));
        properties.put("companyName", textWithKeyword());
        properties.put("location", textWithKeyword());
        properties.put("employmentType", Map.of("type", "keyword"));
        properties.put("remote", Map.of("type", "boolean"));
        properties.put("budgetMin", Map.of("type", "double"));
        properties.put("budgetMax", Map.of("type", "double"));
        properties.put("experienceYears", Map.of("type", "integer"));
        properties.put("status", Map.of("type", "keyword"));
        properties.put("clientId", Map.of("type", "long"));
        properties.put("createdAt", Map.of("type", "date"));
        properties.put("updatedAt", Map.of("type", "date"));
        properties.put("publishedAt", Map.of("type", "date"));
        properties.put("expiresAt", Map.of("type", "date"));
        properties.put("closedAt", Map.of("type", "date"));
        properties.put("tags", Map.of("type", "keyword"));
        return Map.of("mappings", Map.of("properties", properties));
    }

    private Map<String, Object> textWithKeyword() {
        return Map.of(
                "type", "text",
                "fields", Map.of("keyword", Map.of("type", "keyword", "ignore_above", 256))
        );
    }

    private Map<String, Object> toIndexDocument(Job job) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", job.getId());
        document.put("title", job.getTitle());
        document.put("description", job.getDescription());
        document.put("companyName", job.getCompanyName());
        document.put("location", job.getLocation());
        document.put("employmentType", job.getEmploymentType().name());
        document.put("remote", job.isRemote());
        document.put("budgetMin", job.getBudgetMin());
        document.put("budgetMax", job.getBudgetMax());
        document.put("experienceYears", job.getExperienceYears());
        document.put("status", job.getStatus().name());
        document.put("clientId", job.getClientId());
        document.put("createdAt", job.getCreatedAt());
        document.put("updatedAt", job.getUpdatedAt());
        document.put("publishedAt", job.getPublishedAt());
        document.put("expiresAt", job.getExpiresAt());
        document.put("closedAt", job.getClosedAt());
        document.put("tags", job.getTags());
        return document;
    }

    private Map<String, Object> buildSearchRequest(JobSearchRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", request.page() * request.size());
        body.put("size", request.size());
        body.put("query", buildQuery(request));
        body.put("sort", buildSort(request.sort()));
        return body;
    }

    private Map<String, Object> buildQuery(JobSearchRequest request) {
        List<Object> must = new ArrayList<>();
        List<Object> filter = new ArrayList<>();

        if (request.keyword() != null) {
            must.add(Map.of(
                    "multi_match", Map.of(
                            "query", request.keyword(),
                            "fields", List.of("title^5", "companyName^3", "location^2", "description", "tags^2"),
                            "type", "best_fields"
                    )
            ));
        }
        if (request.status() != null) {
            filter.add(Map.of("term", Map.of("status", request.status().name())));
        }
        if (request.clientId() != null) {
            filter.add(Map.of("term", Map.of("clientId", request.clientId())));
        }
        if (request.budgetMin() != null) {
            filter.add(Map.of("range", Map.of("budgetMax", Map.of("gte", request.budgetMin()))));
        }
        if (request.budgetMax() != null) {
            filter.add(Map.of("range", Map.of("budgetMin", Map.of("lte", request.budgetMax()))));
        }
        if (request.location() != null) {
            filter.add(Map.of("match", Map.of("location", Map.of("query", request.location(), "operator", "and"))));
        }
        if (request.companyName() != null) {
            filter.add(Map.of("match", Map.of("companyName", Map.of("query", request.companyName(), "operator", "and"))));
        }
        if (request.employmentType() != null) {
            filter.add(Map.of("term", Map.of("employmentType", request.employmentType().name())));
        }
        if (request.remote() != null) {
            filter.add(Map.of("term", Map.of("remote", request.remote())));
        }
        if (request.experienceYearsMin() != null) {
            filter.add(Map.of("range", Map.of("experienceYears", Map.of("gte", request.experienceYearsMin()))));
        }
        if (request.experienceYearsMax() != null) {
            filter.add(Map.of("range", Map.of("experienceYears", Map.of("lte", request.experienceYearsMax()))));
        }
        if (request.tags() != null) {
            for (String tag : request.tags()) {
                filter.add(Map.of("term", Map.of("tags", tag)));
            }
        }

        Map<String, Object> bool = new LinkedHashMap<>();
        if (!must.isEmpty()) {
            bool.put("must", must);
        }
        if (!filter.isEmpty()) {
            bool.put("filter", filter);
        }
        return bool.isEmpty() ? Map.of("match_all", Map.of()) : Map.of("bool", bool);
    }

    private List<Object> buildSort(JobSearchSort sort) {
        if (sort == null || sort == JobSearchSort.LATEST) {
            return List.of(Map.of("createdAt", Map.of("order", "desc")));
        }
        if (sort == JobSearchSort.SALARY_HIGH) {
            return List.of(
                    Map.of("budgetMax", Map.of("order", "desc")),
                    Map.of("createdAt", Map.of("order", "desc"))
            );
        }
        return List.of(
                Map.of("budgetMin", Map.of("order", "asc")),
                Map.of("createdAt", Map.of("order", "desc"))
        );
    }

    private JobSearchResultItem toSearchResult(JsonNode source) {
        return new JobSearchResultItem(
                readLong(source, "id"),
                readText(source, "title"),
                readText(source, "description"),
                readBigDecimal(source, "budgetMin"),
                readBigDecimal(source, "budgetMax"),
                readTags(source.path("tags")),
                readText(source, "status"),
                readLong(source, "clientId"),
                readNullableText(source, "companyName"),
                readNullableText(source, "location"),
                readText(source, "employmentType"),
                source.path("remote").asBoolean(false),
                source.path("experienceYears").isMissingNode() || source.path("experienceYears").isNull()
                        ? null
                        : source.path("experienceYears").asInt(),
                readInstant(source, "createdAt"),
                readInstant(source, "updatedAt"),
                readInstant(source, "publishedAt"),
                readInstant(source, "expiresAt"),
                readInstant(source, "closedAt")
        );
    }

    private long extractTotalHits(JsonNode totalNode) {
        if (totalNode == null || totalNode.isMissingNode() || totalNode.isNull()) {
            return 0L;
        }
        if (totalNode.isNumber()) {
            return totalNode.asLong();
        }
        return totalNode.path("value").asLong(0L);
    }

    private Long readLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asLong();
    }

    private String readText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private String readNullableText(JsonNode node, String field) {
        return readText(node, field);
    }

    private BigDecimal readBigDecimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return new BigDecimal(value.asText());
    }

    private List<String> readTags(JsonNode tagsNode) {
        if (tagsNode == null || !tagsNode.isArray()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (JsonNode tag : tagsNode) {
            tags.add(tag.asText());
        }
        return List.copyOf(tags);
    }

    private Instant readInstant(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        return Instant.parse(value.asText());
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
