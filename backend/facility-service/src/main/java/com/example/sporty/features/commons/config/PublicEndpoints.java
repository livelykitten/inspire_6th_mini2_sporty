package com.example.sporty.features.commons.config;

import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.http.HttpMethod;

/** Shared by authorization and JWT filtering so public routes cannot drift apart. */
public final class PublicEndpoints {
    private PublicEndpoints() {}

    // 상세 조회는 공개하되 유효한 토큰이 있으면 조회 사용자를 식별한다.
    public static final RequestMatcher MATCH_DETAIL =
            new AndRequestMatcher(
                    PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/matches/{matchId}"),
                    // 내 목록은 로그인 필수이며 공개 상세 조회에 포함하지 않는다.
                    new NegatedRequestMatcher(PathPatternRequestMatcher.withDefaults()
                            .matcher(HttpMethod.GET, "/api/matches/me")));

    public static final RequestMatcher MATCHER = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/users"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/login"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/refresh"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/ai/matches/search"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/matches"),
            MATCH_DETAIL,
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/services"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/services/{serviceId}"));
}
