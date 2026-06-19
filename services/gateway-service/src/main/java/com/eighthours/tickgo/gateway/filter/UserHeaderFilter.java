package com.eighthours.tickgo.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class UserHeaderFilter implements GlobalFilter, Ordered {

    private static final String X_USER_ID = "X-User-Id";
    private static final String DEFAULT_USER_ID = "1";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String userId = request.getHeaders().getFirst(X_USER_ID);

        if (userId == null || userId.trim().isEmpty()) {
            userId = DEFAULT_USER_ID;
        }

        ServerHttpRequest modifiedRequest = request.mutate()
                .header(X_USER_ID, userId)
                .build();

        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
