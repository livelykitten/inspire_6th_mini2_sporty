package com.example.sporty.filter;

import java.util.Optional;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 1차 앱 안의 JWT 검증을 게이트웨이로 옮긴 필터.
 * 서비스는 JWT 를 보지 않고 이 필터가 넣은 X-User-Id 만 믿는다.
 */
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {
    public static final String USER_ID_HEADER = "X-User-Id";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtValidator jwtValidator;

    public AuthenticationFilter(JwtValidator jwtValidator) {
        this.jwtValidator = jwtValidator;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        // ① 밖에서 보낸 X-User-Id 는 믿지 않는다 (아무나 남의 ID 로 위장 가능)
        ServerHttpRequest stripped = request.mutate()
                .headers(headers -> headers.remove(USER_ID_HEADER))
                .build();

        // ② CORS 사전 요청(OPTIONS)에는 토큰이 없다 ③ 공개 경로는 토큰을 보지 않는다
        if (HttpMethod.OPTIONS.equals(request.getMethod()) || PublicEndpoints.isPublic(request)) {
            return chain.filter(exchange.mutate().request(stripped).build());
        }

        // ④ 토큰 검증 ⑤ 통과하면 X-User-Id 설정
        Optional<Long> userId = bearerToken(request).flatMap(jwtValidator::validate);
        if (userId.isPresent()) {
            ServerHttpRequest authenticated = stripped.mutate()
                    .header(USER_ID_HEADER, userId.get().toString())
                    .build();
            return chain.filter(exchange.mutate().request(authenticated).build());
        }

        // ⑦ 선택 경로는 토큰이 없거나 틀려도 헤더 없이 통과
        if (PublicEndpoints.isOptional(request)) {
            return chain.filter(exchange.mutate().request(stripped).build());
        }

        // ⑥ 필수 경로: 401, 본문 없음 (1차와 같음)
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private static Optional<String> bearerToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(header.substring(BEARER_PREFIX.length()));
    }

    @Override
    public int getOrder() {
        // 라우트 필터 중 맨 앞 (요청 로그는 WebFilter 라 이보다 바깥에서 401 까지 남긴다)
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
