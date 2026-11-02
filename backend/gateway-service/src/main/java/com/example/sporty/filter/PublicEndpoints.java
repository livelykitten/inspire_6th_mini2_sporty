package com.example.sporty.filter;

import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/** 1차 PublicEndpoints 를 그대로 옮긴 경로표 (API 명세 탭 인증 칸 기준: 공개 7 · 선택 1, 나머지는 필수). */
final class PublicEndpoints {
    private PublicEndpoints() {}

    private record Rule(HttpMethod method, PathPattern pattern) {
        boolean matches(ServerHttpRequest request) {
            return method.equals(request.getMethod())
                    && pattern.matches(request.getPath().pathWithinApplication());
        }
    }

    private static Rule rule(HttpMethod method, String pattern) {
        return new Rule(method, PathPatternParser.defaultInstance.parse(pattern));
    }

    // 토큰을 보지 않고 통과 (만료된 토큰을 들고 로그인해도 막히지 않도록)
    private static final List<Rule> PUBLIC = List.of(
            rule(HttpMethod.POST, "/api/users"),
            rule(HttpMethod.POST, "/api/auth/login"),
            rule(HttpMethod.POST, "/api/auth/refresh"),
            rule(HttpMethod.POST, "/api/ai/matches/search"),
            rule(HttpMethod.GET, "/api/matches"),
            rule(HttpMethod.GET, "/api/services"),
            rule(HttpMethod.GET, "/api/services/{serviceId}"));

    // 매치 상세는 선택 인증: 맞는 토큰이면 사용자 식별, 틀린 토큰은 비로그인으로 처리 (EM-03)
    private static final Rule MATCH_DETAIL = rule(HttpMethod.GET, "/api/matches/{matchId}");
    // {matchId} 패턴이 me 도 잡으므로 내 매치 목록은 먼저 빼서 필수로 둔다
    private static final Rule MY_MATCHES = rule(HttpMethod.GET, "/api/matches/me");

    static boolean isPublic(ServerHttpRequest request) {
        return PUBLIC.stream().anyMatch(rule -> rule.matches(request));
    }

    static boolean isOptional(ServerHttpRequest request) {
        return MATCH_DETAIL.matches(request) && !MY_MATCHES.matches(request);
    }
}
