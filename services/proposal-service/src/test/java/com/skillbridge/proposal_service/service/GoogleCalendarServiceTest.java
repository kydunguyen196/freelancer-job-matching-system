package com.skillbridge.proposal_service.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.skillbridge.proposal_service.config.CalendarProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

class GoogleCalendarServiceTest {

    @Test
    void createInterviewEventShouldReturnEmptyWhenDisabled() {
        CalendarProperties properties = baseProperties();
        properties.setEnabled(false);
        GoogleCalendarService service = new GoogleCalendarService(properties);

        CalendarService.CreateInterviewEventResult result = service.createInterviewEvent(request());

        assertThat(result.externalEventId()).isNull();
        assertThat(result.warning()).isNull();
    }

    @Test
    void createInterviewEventShouldRefreshTokenAndCreateEvent() throws Exception {
        AtomicInteger tokenCallCount = new AtomicInteger();
        AtomicInteger eventCallCount = new AtomicInteger();
        AtomicReference<String> lastEventAuthHeader = new AtomicReference<>();
        AtomicReference<String> lastEventBody = new AtomicReference<>();

        try (TestServer tokenServer = startServer(exchange -> {
            if ("POST".equals(exchange.getRequestMethod()) && "/token".equals(exchange.getRequestURI().getPath())) {
                tokenCallCount.incrementAndGet();
                writeJson(exchange, 200, "{\"access_token\":\"access-token-123\",\"expires_in\":3600,\"token_type\":\"Bearer\"}");
                return;
            }
            writeJson(exchange, 404, "{}");
        });
             TestServer calendarServer = startServer(exchange -> {
                 if ("POST".equals(exchange.getRequestMethod())
                         && "/calendar/v3/calendars/test-calendar@example.com/events".equals(exchange.getRequestURI().getPath())
                         && "sendUpdates=all".equals(exchange.getRequestURI().getQuery())) {
                     eventCallCount.incrementAndGet();
                     lastEventAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
                     lastEventBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                     writeJson(exchange, 200, "{\"id\":\"google-event-123\"}");
                     return;
                 }
                 writeJson(exchange, 404, "{}");
             })) {

            CalendarProperties properties = baseProperties();
            properties.setTokenUrl(tokenServer.baseUrl() + "/token");
            properties.setApiBaseUrl(calendarServer.baseUrl());

            GoogleCalendarService service = new GoogleCalendarService(properties);
            CalendarService.CreateInterviewEventResult result = service.createInterviewEvent(request());

            assertThat(result.externalEventId()).isEqualTo("google-event-123");
            assertThat(result.warning()).isNull();
            assertThat(tokenCallCount.get()).isEqualTo(1);
            assertThat(eventCallCount.get()).isEqualTo(1);
            assertThat(lastEventAuthHeader.get()).isEqualTo("Bearer access-token-123");
            assertThat(lastEventBody.get()).contains("\"summary\":\"Interview - Backend Engineer\"");
            assertThat(lastEventBody.get()).contains("\"email\":\"candidate@example.com\"");
            assertThat(lastEventBody.get()).contains("\"email\":\"recruiter@example.com\"");
            assertThat(lastEventBody.get()).contains("Price: 1200");
            assertThat(lastEventBody.get()).contains("Duration Days: 21");
            assertThat(lastEventBody.get()).contains("Meeting Link: https://meet.google.com/test-room");
        }
    }

    private CalendarProperties baseProperties() {
        CalendarProperties properties = new CalendarProperties();
        properties.setEnabled(true);
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
        properties.setRedirectUri("http://localhost:8080/oauth2/callback/google");
        properties.setCalendarId("test-calendar@example.com");
        properties.setScopes("https://www.googleapis.com/auth/calendar");
        properties.setRefreshToken("refresh-token");
        return properties;
    }

    private CalendarService.CreateInterviewEventRequest request() {
        return new CalendarService.CreateInterviewEventRequest(
                1L,
                2L,
                "Backend Engineer",
                3L,
                "candidate@example.com",
                4L,
                "recruiter@example.com",
                java.math.BigDecimal.valueOf(1200),
                21,
                "Built similar hiring platforms before.",
                Instant.parse("2026-03-20T03:00:00Z"),
                Instant.parse("2026-03-20T04:00:00Z"),
                "https://meet.google.com/test-room",
                "Please prepare your portfolio"
        );
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
