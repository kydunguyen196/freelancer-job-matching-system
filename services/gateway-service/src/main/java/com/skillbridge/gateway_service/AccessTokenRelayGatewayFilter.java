package com.skillbridge.gateway_service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class AccessTokenRelayGatewayFilter implements GlobalFilter, Ordered {

    private static final String AUTH_PATH_PREFIX = "/auth/";
    private static final String AUTHORIZATION_PREFIX = "Bearer ";

    private final String accessCookieName;

    public AccessTokenRelayGatewayFilter(
            @Value("${app.auth.cookie.access-name:sb_access_token}") String accessCookieName
    ) {
        this.accessCookieName = accessCookieName;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        if (path != null && path.startsWith(AUTH_PATH_PREFIX)) {
            return chain.filter(exchange);
        }

        ServerHttpRequest.Builder builder = request.mutate();
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization)) {
            String token = resolveAccessToken(request);
            if (StringUtils.hasText(token)) {
                builder.header(HttpHeaders.AUTHORIZATION, AUTHORIZATION_PREFIX + token);
            }
        }

        if (request.getMethod() != HttpMethod.OPTIONS) {
            builder.headers(headers -> headers.remove(HttpHeaders.COOKIE));
        }

        return chain.filter(exchange.mutate().request(builder.build()).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private String resolveAccessToken(ServerHttpRequest request) {
        HttpCookie cookie = request.getCookies().getFirst(accessCookieName);
        if (cookie == null || !StringUtils.hasText(cookie.getValue())) {
            return null;
        }
        return cookie.getValue().trim();
    }
}
