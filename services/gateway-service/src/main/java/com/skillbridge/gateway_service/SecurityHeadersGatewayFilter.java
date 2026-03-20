package com.skillbridge.gateway_service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class SecurityHeadersGatewayFilter implements GlobalFilter, Ordered {

    private final boolean hstsEnabled;
    private final long hstsMaxAgeSeconds;

    public SecurityHeadersGatewayFilter(
            @Value("${app.security.hsts.enabled:false}") boolean hstsEnabled,
            @Value("${app.security.hsts.max-age-seconds:31536000}") long hstsMaxAgeSeconds
    ) {
        this.hstsEnabled = hstsEnabled;
        this.hstsMaxAgeSeconds = hstsMaxAgeSeconds;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpHeaders headers = exchange.getResponse().getHeaders();
        headers.addIfAbsent("X-Content-Type-Options", "nosniff");
        headers.addIfAbsent("X-Frame-Options", "DENY");
        headers.addIfAbsent("Referrer-Policy", "strict-origin-when-cross-origin");
        headers.addIfAbsent("Permissions-Policy", "camera=(), microphone=(), geolocation=()");

        if (hstsEnabled) {
            headers.addIfAbsent("Strict-Transport-Security", "max-age=" + hstsMaxAgeSeconds + "; includeSubDomains");
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 30;
    }
}
