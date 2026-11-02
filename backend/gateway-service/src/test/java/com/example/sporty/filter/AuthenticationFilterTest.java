package com.example.sporty.filter;

import static org.junit.jupiter.api.Assertions.*;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** 1차 JwtAuthenticationFilterTest 케이스를 게이트웨이 필터로 옮기고, 헤더 제거 · 공개/선택 경로 케이스를 더함. */
class AuthenticationFilterTest {
    private static final String SECRET = "test-only-signing-secret-with-at-least-32-bytes";
    private static final String USER_ID = AuthenticationFilter.USER_ID_HEADER;

    private AuthenticationFilter filter;
    // 다음 단계(서비스)로 넘어간 요청. null 이면 게이트웨이에서 막힘
    private AtomicReference<ServerWebExchange> forwarded;
    private GatewayFilterChain chain;

    @BeforeEach
    void setup() {
        filter = new AuthenticationFilter(new JwtValidator(SECRET));
        forwarded = new AtomicReference<>();
        chain = exchange -> {
            forwarded.set(exchange);
            return Mono.empty();
        };
    }

    private static String token(String subject, String tokenType, Date expiration, String secret) {
        var builder = Jwts.builder().claim("tokenType", tokenType).setExpiration(expiration);
        if (subject != null) builder.setSubject(subject);
        return builder.signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private static String token(String subject, String tokenType, Date expiration) {
        return token(subject, tokenType, expiration, SECRET);
    }

    private static String accessToken(String subject) {
        return token(subject, "ACCESS", new Date(System.currentTimeMillis() + 300000));
    }

    private MockServerWebExchange run(MockServerHttpRequest.BaseBuilder<?> request) {
        var exchange = MockServerWebExchange.from(request);
        filter.filter(exchange, chain).block();
        return exchange;
    }

    private static MockServerHttpRequest.BaseBuilder<?> withToken(MockServerHttpRequest.BaseBuilder<?> request,
            String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private void assertRejected(MockServerWebExchange exchange) {
        assertNull(forwarded.get(), "막혀야 할 요청이 서비스까지 갔다");
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    private String forwardedUserId() {
        assertNotNull(forwarded.get(), "통과해야 할 요청이 막혔다");
        return forwarded.get().getRequest().getHeaders().getFirst(USER_ID);
    }

    // ---- 1차 JwtAuthenticationFilterTest 에서 옮긴 케이스 ----

    @ParameterizedTest
    @ValueSource(strings = {"1", "2147483648", "9223372036854775807"})
    void validTokenSetsUserIdHeader(String subject) {
        run(withToken(MockServerHttpRequest.post("/api/matches"), accessToken(subject)));
        assertEquals(subject, forwardedUserId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"member@example.com", "not-a-number", "0", "-1", "9223372036854775808"})
    void invalidUserIdIsRejected(String subject) {
        assertRejected(run(withToken(MockServerHttpRequest.post("/api/matches"), accessToken(subject))));
    }

    @Test
    void tokenWithoutSubjectIsRejected() {
        assertRejected(run(withToken(MockServerHttpRequest.post("/api/matches"), accessToken(null))));
    }

    @Test
    void blankSubjectIsRejected() {
        assertRejected(run(withToken(MockServerHttpRequest.post("/api/matches"), accessToken("   "))));
    }

    @Test
    void tokenWithoutExpirationIsRejected() {
        assertRejected(run(withToken(MockServerHttpRequest.post("/api/matches"), token("1", "ACCESS", null))));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"REFRESH", "UNKNOWN"})
    void nonAccessTokenIsRejected(String tokenType) {
        String token = token("1", tokenType, new Date(System.currentTimeMillis() + 300000));
        assertRejected(run(withToken(MockServerHttpRequest.post("/api/matches"), token)));
    }

    @Test
    void downstreamFailureIsNotAuthenticationFailure() {
        var exchange = MockServerWebExchange.from(
                withToken(MockServerHttpRequest.post("/api/matches"), accessToken("1")));
        GatewayFilterChain failing = ex -> Mono.error(new IllegalStateException("downstream failure"));
        StepVerifier.create(filter.filter(exchange, failing))
                .expectError(IllegalStateException.class)
                .verify();
        assertNotEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    // ---- 게이트웨이에서 새로 생긴 케이스 ----

    @Test
    void requiredPathWithoutTokenIsRejected() {
        assertRejected(run(MockServerHttpRequest.get("/api/users/me")));
    }

    @Test
    void spoofedUserIdHeaderAloneIsRejected() {
        assertRejected(run(MockServerHttpRequest.get("/api/users/me").header(USER_ID, "999")));
    }

    @Test
    void spoofedUserIdHeaderIsReplacedByTokenSubject() {
        run(withToken(MockServerHttpRequest.get("/api/users/me").header(USER_ID, "999"), accessToken("1")));
        assertEquals("1", forwardedUserId());
    }

    @Test
    void tokenSignedWithOtherSecretIsRejected() {
        String token = token("1", "ACCESS", new Date(System.currentTimeMillis() + 300000),
                "another-secret-that-is-also-at-least-32-bytes");
        assertRejected(run(withToken(MockServerHttpRequest.get("/api/users/me"), token)));
    }

    @Test
    void publicPathSkipsTokenAndRemovesSpoofedHeader() {
        run(withToken(MockServerHttpRequest.post("/api/auth/login").header(USER_ID, "999"), "wrong-token"));
        assertNull(forwardedUserId());
    }

    @Test
    void preflightPassesWithoutToken() {
        run(MockServerHttpRequest.method(HttpMethod.OPTIONS, "/api/users/me"));
        assertNull(forwardedUserId());
    }

    @Test
    void matchDetailWithWrongTokenPassesAsAnonymous() {
        run(withToken(MockServerHttpRequest.get("/api/matches/1"), "wrong-token"));
        assertNull(forwardedUserId());
    }

    @Test
    void matchDetailWithValidTokenSetsUserId() {
        run(withToken(MockServerHttpRequest.get("/api/matches/1"), accessToken("7")));
        assertEquals("7", forwardedUserId());
    }

    @Test
    void myMatchesIsNotTreatedAsMatchDetail() {
        assertRejected(run(MockServerHttpRequest.get("/api/matches/me")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/matches", "/api/services", "/api/services/3"})
    void publicGetPathsPassWithoutToken(String path) {
        run(MockServerHttpRequest.get(path));
        assertNull(forwardedUserId());
    }

    @Test
    void publicPathIsMethodSpecific() {
        // GET /api/matches 는 공개지만 POST(매치 개설)는 로그인 필수
        assertRejected(run(MockServerHttpRequest.post("/api/matches")));
    }
}
