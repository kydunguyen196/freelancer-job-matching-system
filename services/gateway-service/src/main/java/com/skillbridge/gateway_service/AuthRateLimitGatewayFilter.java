package com.skillbridge.gateway_service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class AuthRateLimitGatewayFilter implements GlobalFilter, Ordered {

    private static final String LOGIN_PATH = "/auth/login";
    private static final String REGISTER_PATH = "/auth/register";
    private static final String REFRESH_PATH = "/auth/refresh";

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final boolean enabled;
    private final boolean trustForwardedFor;
    private final long windowMillis;
    private final int loginLimit;
    private final int registerLimit;
    private final int refreshLimit;

    public AuthRateLimitGatewayFilter(
            @Value("${app.rate-limit.auth.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.auth.trust-forwarded-for:true}") boolean trustForwardedFor,
            @Value("${app.rate-limit.auth.window-seconds:60}") long windowSeconds,
            @Value("${app.rate-limit.auth.login-max-requests:10}") int loginLimit,
            @Value("${app.rate-limit.auth.register-max-requests:5}") int registerLimit,
            @Value("${app.rate-limit.auth.refresh-max-requests:20}") int refreshLimit
    ) {
        this.enabled = enabled;
        this.trustForwardedFor = trustForwardedFor;
        this.windowMillis = Duration.ofSeconds(windowSeconds).toMillis();
        this.loginLimit = loginLimit;
        this.registerLimit = registerLimit;
        this.refreshLimit = refreshLimit;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        if (!"POST".equalsIgnoreCase(request.getMethod().name())) {
            return chain.filter(exchange);
        }

        String path = request.getURI().getPath();
        int limit = resolveLimit(path);
        if (limit <= 0) {
            return chain.filter(exchange);
        }

        long now = System.currentTimeMillis();
        String clientKey = resolveClientKey(request);
        String counterKey = path + "|" + clientKey;
        WindowCounter counter = counters.computeIfAbsent(counterKey, key -> new WindowCounter(now));
        boolean allowed = counter.tryAcquire(now, windowMillis, limit);

        if (!allowed) {
            if (counter.isExpired(now, windowMillis)) {
                counters.remove(counterKey, counter);
            }
            return reject(exchange);
        }

        if (counter.isExpired(now, windowMillis * 2)) {
            counters.remove(counterKey, counter);
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    private int resolveLimit(String path) {
        return switch (path) {
            case LOGIN_PATH -> loginLimit;
            case REGISTER_PATH -> registerLimit;
            case REFRESH_PATH -> refreshLimit;
            default -> -1;
        };
    }

    private String resolveClientKey(ServerHttpRequest request) {
        if (trustForwardedFor) {
            String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",")[0].trim();
            }
        }

        if (request.getRemoteAddress() == null || request.getRemoteAddress().getAddress() == null) {
            return "unknown";
        }
        return request.getRemoteAddress().getAddress().getHostAddress();
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, "60");

        byte[] payload = """
                {"message":"Too many authentication attempts. Please retry later."}
                """.trim().getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(payload);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private static final class WindowCounter {
        private long windowStartedAtMillis;
        private int count;

        private WindowCounter(long now) {
            this.windowStartedAtMillis = now;
        }

        private synchronized boolean tryAcquire(long now, long windowMillis, int limit) {
            if (now - windowStartedAtMillis >= windowMillis) {
                windowStartedAtMillis = now;
                count = 0;
            }
            count += 1;
            return count <= limit;
        }

        private synchronized boolean isExpired(long now, long ttlMillis) {
            return now - windowStartedAtMillis >= ttlMillis;
        }
    }
}
