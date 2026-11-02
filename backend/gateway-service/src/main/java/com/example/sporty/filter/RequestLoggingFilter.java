package com.example.sporty.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

// 모든 요청의 메서드 · 경로 · 응답 코드 · 처리 시간을 로그로 남김
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.currentTimeMillis();
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getRawPath();

        // 성공 · 실패(401 · 503 등) 모두 기록되도록 doFinally 사용
        return chain.filter(exchange).doFinally(signal ->
                log.info("[GATEWAY] {} {} -> {} ({}ms)", method, path,
                        exchange.getResponse().getStatusCode(),
                        System.currentTimeMillis() - start));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
