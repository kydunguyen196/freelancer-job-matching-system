package com.skillbridge.proposal_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.proposal_service.domain.Proposal;
import com.skillbridge.proposal_service.domain.ProposalStatus;
import com.skillbridge.proposal_service.dto.CreateProposalRequest;
import com.skillbridge.proposal_service.dto.ProposalResponse;
import com.skillbridge.proposal_service.messaging.ProposalEventPublisher;
import com.skillbridge.proposal_service.repository.ProposalRepository;
import com.skillbridge.proposal_service.security.JwtUserPrincipal;

@ExtendWith(MockitoExtension.class)
class ProposalServiceTest {

    @Mock
    private ProposalRepository proposalRepository;

    @Mock
    private ProposalEventPublisher proposalEventPublisher;

    @Test
    void createProposalShouldRequireFreelancerRole() {
        ProposalService proposalService = new ProposalService(
                proposalRepository,
                proposalEventPublisher,
                "http://localhost:65530",
                "http://localhost:65531",
                "internal-key"
        );
        CreateProposalRequest request = new CreateProposalRequest(
                9L,
                "I can help",
                BigDecimal.valueOf(300),
                8
        );
        JwtUserPrincipal client = new JwtUserPrincipal(91L, "client@example.com", "CLIENT");

        assertThatThrownBy(() -> proposalService.createProposal(request, client))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void createProposalShouldRejectDuplicateApplication() throws Exception {
        try (TestServer jobServer = startServer(exchange -> {
            if ("GET".equals(exchange.getRequestMethod()) && exchange.getRequestURI().getPath().equals("/jobs/11")) {
                writeJson(exchange, 200, "{\"id\":11,\"clientId\":99,\"status\":\"OPEN\"}");
                return;
            }
            writeJson(exchange, 404, "{\"message\":\"not found\"}");
        });
             TestServer contractServer = startServer(exchange -> writeJson(exchange, 200, "{}"))) {

            ProposalService proposalService = new ProposalService(
                    proposalRepository,
                    proposalEventPublisher,
                    jobServer.baseUrl(),
                    contractServer.baseUrl(),
                    "internal-key"
            );

            CreateProposalRequest request = new CreateProposalRequest(
                    11L,
                    "I can do this project",
                    BigDecimal.valueOf(250),
                    7
            );
            JwtUserPrincipal freelancer = new JwtUserPrincipal(501L, "freelancer@example.com", "FREELANCER");
            when(proposalRepository.existsByJobIdAndFreelancerId(11L, 501L)).thenReturn(true);

            assertThatThrownBy(() -> proposalService.createProposal(request, freelancer))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

            verify(proposalRepository, never()).save(any(Proposal.class));
        }
    }

    @Test
    void createProposalShouldRejectClosedJob() throws Exception {
        try (TestServer jobServer = startServer(exchange -> {
            if ("GET".equals(exchange.getRequestMethod()) && exchange.getRequestURI().getPath().equals("/jobs/21")) {
                writeJson(exchange, 200, "{\"id\":21,\"clientId\":77,\"status\":\"CLOSED\"}");
                return;
            }
            writeJson(exchange, 404, "{\"message\":\"not found\"}");
        });
             TestServer contractServer = startServer(exchange -> writeJson(exchange, 200, "{}"))) {

            ProposalService proposalService = new ProposalService(
                    proposalRepository,
                    proposalEventPublisher,
                    jobServer.baseUrl(),
                    contractServer.baseUrl(),
                    "internal-key"
            );

            CreateProposalRequest request = new CreateProposalRequest(
                    21L,
                    "I can do this project",
                    BigDecimal.valueOf(250),
                    7
            );
            JwtUserPrincipal freelancer = new JwtUserPrincipal(501L, "freelancer@example.com", "FREELANCER");

            assertThatThrownBy(() -> proposalService.createProposal(request, freelancer))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    @Test
    void acceptProposalShouldCreateContractAndPublishEvent() throws Exception {
        AtomicInteger contractCallCount = new AtomicInteger();
        AtomicReference<String> lastInternalApiKey = new AtomicReference<>();
        AtomicReference<String> lastBody = new AtomicReference<>();

        try (TestServer jobServer = startServer(exchange -> {
            if ("GET".equals(exchange.getRequestMethod()) && exchange.getRequestURI().getPath().equals("/jobs/31")) {
                writeJson(exchange, 200, "{\"id\":31,\"clientId\":900,\"status\":\"OPEN\"}");
                return;
            }
            writeJson(exchange, 404, "{\"message\":\"not found\"}");
        });
             TestServer contractServer = startServer(exchange -> {
                 if ("POST".equals(exchange.getRequestMethod())
                         && exchange.getRequestURI().getPath().equals("/contracts/internal/from-proposal")) {
                     contractCallCount.incrementAndGet();
                     lastInternalApiKey.set(exchange.getRequestHeaders().getFirst("X-Internal-Api-Key"));
                     lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                     writeJson(exchange, 200, "{\"status\":\"ok\"}");
                     return;
                 }
                 writeJson(exchange, 404, "{\"message\":\"not found\"}");
             })) {

            ProposalService proposalService = new ProposalService(
                    proposalRepository,
                    proposalEventPublisher,
                    jobServer.baseUrl(),
                    contractServer.baseUrl(),
                    "internal-key"
            );

            Proposal pending = new Proposal();
            pending.setId(444L);
            pending.setJobId(31L);
            pending.setFreelancerId(777L);
            pending.setFreelancerEmail("freelancer@example.com");
            pending.setPrice(BigDecimal.valueOf(400));
            pending.setDurationDays(10);
            pending.setStatus(ProposalStatus.PENDING);

            when(proposalRepository.findById(444L)).thenReturn(Optional.of(pending));
            when(proposalRepository.save(any(Proposal.class))).thenAnswer(invocation -> invocation.getArgument(0));

            JwtUserPrincipal client = new JwtUserPrincipal(900L, "client@example.com", "CLIENT");
            ProposalResponse response = proposalService.acceptProposal(444L, client);

            assertThat(response.status()).isEqualTo("ACCEPTED");
            assertThat(response.acceptedByClientId()).isEqualTo(900L);
            assertThat(response.acceptedAt()).isNotNull();

            assertThat(contractCallCount.get()).isEqualTo(1);
            assertThat(lastInternalApiKey.get()).isEqualTo("internal-key");
            assertThat(lastBody.get()).contains("\"proposalId\":444");
            assertThat(lastBody.get()).contains("\"jobId\":31");
            assertThat(lastBody.get()).contains("\"clientId\":900");
            assertThat(lastBody.get()).contains("\"freelancerId\":777");
            verify(proposalEventPublisher).publishProposalAccepted(any(Proposal.class), org.mockito.Mockito.eq(900L));
        }
    }

    @Test
    void listProposalsByJobShouldRejectNonOwnerClient() throws Exception {
        try (TestServer jobServer = startServer(exchange -> {
            if ("GET".equals(exchange.getRequestMethod()) && exchange.getRequestURI().getPath().equals("/jobs/55")) {
                writeJson(exchange, 200, "{\"id\":55,\"clientId\":300,\"status\":\"OPEN\"}");
                return;
            }
            writeJson(exchange, 404, "{\"message\":\"not found\"}");
        });
             TestServer contractServer = startServer(exchange -> writeJson(exchange, 200, "{}"))) {

            ProposalService proposalService = new ProposalService(
                    proposalRepository,
                    proposalEventPublisher,
                    jobServer.baseUrl(),
                    contractServer.baseUrl(),
                    "internal-key"
            );

            JwtUserPrincipal otherClient = new JwtUserPrincipal(999L, "other@example.com", "CLIENT");

            assertThatThrownBy(() -> proposalService.listProposalsByJob(55L, otherClient, 0, 20))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        }
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
