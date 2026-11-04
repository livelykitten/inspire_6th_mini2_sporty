package com.example.sporty.features.commons.config;

import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.http.HttpMethod;

/** AI 서비스에서 인증 없이 호출할 수 있는 기능 API. */
public final class PublicEndpoints {
    private PublicEndpoints() {}

    public static final RequestMatcher MATCHER = PathPatternRequestMatcher.withDefaults()
            .matcher(HttpMethod.POST, "/api/ai/matches/search");
}
