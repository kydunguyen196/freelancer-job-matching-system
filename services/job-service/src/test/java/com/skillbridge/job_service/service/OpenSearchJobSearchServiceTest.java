package com.skillbridge.job_service.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.skillbridge.job_service.config.SearchProperties;
import com.skillbridge.job_service.domain.EmploymentType;
import com.skillbridge.job_service.domain.Job;
import com.skillbridge.job_service.domain.JobStatus;
import com.skillbridge.job_service.dto.PagedResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

class OpenSearchJobSearchServiceTest {

    @Test
    void supportsIndexingShouldBeFalseWhenUrlMissing() {
        SearchProperties properties = new SearchProperties();
        properties.setEnabled(true);
        properties.setProvider("opensearch");

        OpenSearchJobSearchService service = new OpenSearchJobSearchService(properties);

        assertThat(service.supportsIndexing()).isFalse();
    }

    @Test
    void searchShouldCreateIndexAndParseHits() throws Exception {
        AtomicBoolean indexCreated = new AtomicBoolean(false);
        AtomicReference<String> lastCreateBody = new AtomicReference<>();
        AtomicReference<String> lastSearchBody = new AtomicReference<>();

        try (TestServer server = startServer(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("GET".equals(exchange.getRequestMethod()) && "/jobs".equals(path)) {
                if (indexCreated.get()) {
                    writeJson(exchange, 200, "{\"jobs\":{\"aliases\":{}}}");
                } else {
                    writeJson(exchange, 404, "{}");
                }
                return;
            }
            if ("PUT".equals(exchange.getRequestMethod()) && "/jobs".equals(path)) {
                indexCreated.set(true);
                lastCreateBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                writeJson(exchange, 200, "{\"acknowledged\":true}");
                return;
            }
            if ("POST".equals(exchange.getRequestMethod()) && "/jobs/_search".equals(path)) {
                lastSearchBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                writeJson(exchange, 200, """
                        {"hits":{"total":{"value":1},"hits":[{"_source":{
                          "id":10,
                          "title":"Backend Engineer",
                          "description":"Build search services",
                          "budgetMin":1200,
                          "budgetMax":2400,
                          "tags":["java","search"],
                          "status":"OPEN",
                          "clientId":99,
                          "companyName":"Acme",
                          "location":"Ho Chi Minh City",
                          "employmentType":"FULL_TIME",
                          "remote":true,
                          "experienceYears":3,
                          "createdAt":"2026-03-19T01:00:00Z",
                          "updatedAt":"2026-03-19T02:00:00Z",
                          "publishedAt":"2026-03-19T03:00:00Z",
                          "expiresAt":"2026-03-29T03:00:00Z",
                          "closedAt":null
                        }}]}}
                        """);
                return;
            }
            writeJson(exchange, 404, "{}");
        })) {
            SearchProperties properties = properties(server.baseUrl());
            OpenSearchJobSearchService service = new OpenSearchJobSearchService(properties);

            PagedResult<JobSearchResultItem> result = service.search(new JobSearchRequest(
                    "backend engineer",
                    JobStatus.OPEN,
                    BigDecimal.valueOf(1000),
                    BigDecimal.valueOf(3000),
                    null,
                    List.of("java"),
                    "Ho Chi Minh",
                    "Acme",
                    EmploymentType.FULL_TIME,
                    true,
                    2,
                    5,
                    JobSearchSort.SALARY_HIGH,
                    0,
                    20
            ));

            assertThat(result.totalElements()).isEqualTo(1);
            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).title()).isEqualTo("Backend Engineer");
            assertThat(result.content().get(0).budgetMax()).isEqualByComparingTo("2400");
            assertThat(lastCreateBody.get()).contains("\"title\"");
            assertThat(lastCreateBody.get()).contains("\"companyName\"");
            assertThat(lastSearchBody.get()).contains("\"title^5\"");
            assertThat(lastSearchBody.get()).contains("\"companyName^3\"");
            assertThat(lastSearchBody.get()).contains("\"remote\":true");
            assertThat(lastSearchBody.get()).contains("\"budgetMax\":{\"order\":\"desc\"}");
        }
    }

    @Test
    void indexJobShouldUpsertDocument() throws Exception {
        AtomicReference<String> lastIndexBody = new AtomicReference<>();

        try (TestServer server = startServer(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("GET".equals(exchange.getRequestMethod()) && "/jobs".equals(path)) {
                writeJson(exchange, 200, "{\"jobs\":{\"aliases\":{}}}");
                return;
            }
            if ("PUT".equals(exchange.getRequestMethod()) && "/jobs/_doc/10".equals(path)) {
                lastIndexBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                writeJson(exchange, 200, "{\"result\":\"updated\"}");
                return;
            }
            writeJson(exchange, 404, "{}");
        })) {
            SearchProperties properties = properties(server.baseUrl());
            OpenSearchJobSearchService service = new OpenSearchJobSearchService(properties);
            Job job = new Job();
            job.setId(10L);
            job.setTitle("Backend Engineer");
            job.setDescription("Build search services");
            job.setCompanyName("Acme");
            job.setLocation("Ho Chi Minh City");
            job.setBudgetMin(BigDecimal.valueOf(1200));
            job.setBudgetMax(BigDecimal.valueOf(2400));
            job.setTags(List.of("java", "search"));
            job.setStatus(JobStatus.OPEN);
            job.setEmploymentType(EmploymentType.FULL_TIME);
            job.setRemote(true);
            job.setExperienceYears(3);
            job.setClientId(99L);
            job.setCreatedAt(Instant.parse("2026-03-19T01:00:00Z"));
            job.setUpdatedAt(Instant.parse("2026-03-19T02:00:00Z"));
            job.setPublishedAt(Instant.parse("2026-03-19T03:00:00Z"));

            boolean indexed = service.indexJob(job);

            assertThat(indexed).isTrue();
            assertThat(lastIndexBody.get()).contains("\"title\":\"Backend Engineer\"");
            assertThat(lastIndexBody.get()).contains("\"status\":\"OPEN\"");
            assertThat(lastIndexBody.get()).contains("\"tags\":[\"java\",\"search\"]");
        }
    }

    private SearchProperties properties(String url) {
        SearchProperties properties = new SearchProperties();
        properties.setEnabled(true);
        properties.setProvider("opensearch");
        properties.getOpensearch().setUrl(url);
        properties.getOpensearch().setIndexJobs("jobs");
        properties.getOpensearch().setConnectTimeoutMs(1000);
        properties.getOpensearch().setSocketTimeoutMs(1000);
        return properties;
    }

    private TestServer startServer(HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", handler);
        server.start();
        return new TestServer(server);
    }

    private void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private static final class TestServer implements AutoCloseable {
        private final HttpServer server;

        private TestServer(HttpServer server) {
            this.server = server;
        }

        private String baseUrl() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
