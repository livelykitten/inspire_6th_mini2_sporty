package com.example.sporty.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

// 모든 요청의 메서드 · 경로 · 응답 코드 · 처리 시간을 로그로 남김.
// GlobalFilter 는 라우트가 있는 요청에서만 돌아서, 라우트가 없는 /internal/** (404) 도 남도록 WebFilter 로 둔다 (W-26)
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long start = System.currentTimeMillis();
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getRawPath();

        // 성공 · 실패(401 · 404 · 503 등) 모두 기록되도록 doFinally 사용
        return chain.filter(exchange).doFinally(signal ->
                log.info("[GATEWAY] {} {} -> {} ({}ms)", method, path,
                        exchange.getResponse().getStatusCode(),
                        System.currentTimeMillis() - start));
    }
}
