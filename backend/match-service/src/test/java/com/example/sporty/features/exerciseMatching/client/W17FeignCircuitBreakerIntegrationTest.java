package com.example.sporty.features.exerciseMatching.client;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.example.sporty.features.commons.exception.matches.MatchUserNotFoundException;
import com.example.sporty.features.commons.handler.GlobalExceptionHandler;

/** Real Feign + Spring Cloud CircuitBreaker + Resilience4j, with a loopback HTTP server.
 * Loads production application.yml (including ignoreExceptions); only infrastructure and
 * timing/window settings are overridden. No fallback factory is called directly.
 */
@SpringBootTest(classes = W17FeignCircuitBreakerIntegrationTest.Config.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.config.import=",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "resilience4j.circuitbreaker.configs.default.slidingWindowType=COUNT_BASED",
        "resilience4j.circuitbreaker.configs.default.slidingWindowSize=4",
        "resilience4j.circuitbreaker.configs.default.minimumNumberOfCalls=4",
        "resilience4j.circuitbreaker.configs.default.failureRateThreshold=50",
        "resilience4j.circuitbreaker.configs.default.waitDurationInOpenState=1h"
})
class W17FeignCircuitBreakerIntegrationTest {
    static final String STATUS = "UserServiceClientgetUserStatusLong";
    static final String PROFILES = "UserServiceClientgetUserProfilesList";
    static final AtomicInteger requests = new AtomicInteger();
    static final AtomicInteger responseStatus = new AtomicInteger(200);
    static final HttpServer server = startServer();

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class, KafkaAutoConfiguration.class})
    @EnableFeignClients(clients = UserServiceClient.class)
    @Import({UserServiceFallbackFactory.class, MemberLookupImpl.class})
    static class Config {}

    @DynamicPropertySource
    static void stubUrl(DynamicPropertyRegistry properties) {
        properties.add("spring.cloud.openfeign.client.config.user-service.url",
                () -> "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @Autowired UserServiceClient client;
    @Autowired MemberLookupImpl lookup;
    @Autowired CircuitBreakerRegistry registry;

    @BeforeEach
    void reset() {
        registry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        requests.set(0);
        responseStatus.set(200);
        assertThat(Proxy.isProxyClass(client.getClass())).isTrue();
    }

    @AfterAll
    static void stopServer() { server.stop(0); }

    @Test
    void repeatedStatus404IsIgnoredAndDoesNotBlockSubsequentActiveUser() {
        responseStatus.set(404);
        for (int i = 0; i < 6; i++) {
            var error = catchThrowableOfType(() -> lookup.validateActive(7L), MatchUserNotFoundException.class);
            var response = new GlobalExceptionHandler().handleMatchUserNotFoundException(error);
            assertThat(response.getStatusCode().value()).isEqualTo(401);
            assertThat(response.getBody().getCode()).isEqualTo("MATCH_USER_NOT_FOUND");
        }
        var breaker = breaker(STATUS);
        assertIgnored(breaker);
        assertThat(requests.get()).isEqualTo(6);
        responseStatus.set(200);
        assertThatCode(() -> lookup.validateActive(7L)).doesNotThrowAnyException();
        assertThat(requests.get()).isEqualTo(7);
        assertThat(breaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        report(breaker);
    }

    @Test
    void repeatedProfile500IsNotHiddenByEmptyFallbackAndIsIgnoredByBreaker() {
        responseStatus.set(500);
        for (int i = 0; i < 6; i++) {
            var error = catchThrowableOfType(() -> lookup.getProfiles(List.of(7L)), IllegalStateException.class);
            assertThat(error).hasCauseInstanceOf(FeignException.InternalServerError.class);
            assertThat(new GlobalExceptionHandler().handleUnexpectedException(error).getStatusCode().value())
                    .isEqualTo(500);
        }
        var breaker = breaker(PROFILES);
        assertIgnored(breaker);
        assertThat(requests.get()).isEqualTo(6);
        responseStatus.set(200);
        assertThat(lookup.getProfiles(List.of(7L))).containsEntry(7L, new MemberProfile(107L, "member", "image"));
        assertThat(requests.get()).isEqualTo(7);
        assertThat(breaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        report(breaker);
    }

    @Test
    void repeatedProfile503OpensCircuitAndNextCallFallsBackWithoutHttp() {
        responseStatus.set(503);
        for (int i = 0; i < 4; i++) {
            assertThat(lookup.getProfiles(List.of(7L))).isEmpty();
        }
        var breaker = breaker(PROFILES);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(4);
        assertThat(breaker.getMetrics().getFailureRate()).isEqualTo(100f);
        assertThat(requests.get()).isEqualTo(4);
        // Even if the server recovers, the OPEN circuit must not send another request.
        responseStatus.set(200);
        assertThat(lookup.getProfiles(List.of(7L))).isEmpty();
        assertThat(requests.get()).isEqualTo(4);
        assertThat(breaker.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(1);
        // IN-01 still succeeds while the IN-02 circuit is OPEN.
        lookup.validateActive(7L);
        var statusBreaker = breaker(STATUS);
        assertThat(statusBreaker).isNotSameAs(breaker);
        assertThat(statusBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(statusBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(requests.get()).isEqualTo(5);
        report(breaker);
        report(statusBreaker);
    }

    CircuitBreaker breaker(String name) {
        // find(), not circuitBreaker(), so this assertion cannot create a false-positive instance.
        return registry.find(name).orElseThrow(() -> new AssertionError("Missing breaker " + name
                + "; actual=" + registry.getAllCircuitBreakers().stream().map(CircuitBreaker::getName).toList()));
    }

    void assertIgnored(CircuitBreaker breaker) {
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(breaker.getMetrics().getNumberOfBufferedCalls()).isZero();
        assertThat(breaker.getMetrics().getNumberOfSuccessfulCalls()).isZero();
        report(breaker);
    }

    void report(CircuitBreaker breaker) {
        System.out.printf("W17 breaker=%s state=%s failed=%d successful=%d buffered=%d rejected=%d%n",
                breaker.getName(), breaker.getState(), breaker.getMetrics().getNumberOfFailedCalls(),
                breaker.getMetrics().getNumberOfSuccessfulCalls(), breaker.getMetrics().getNumberOfBufferedCalls(),
                breaker.getMetrics().getNumberOfNotPermittedCalls());
    }

    static HttpServer startServer() {
        try {
            var stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            stub.createContext("/internal/users", exchange -> {
                requests.incrementAndGet();
                int code = responseStatus.get();
                String body = code == 200
                        ? (exchange.getRequestURI().getPath().endsWith("/profiles")
                            ? "[{\"userId\":7,\"profileId\":107,\"nickname\":\"member\",\"imageUrl\":\"image\"}]"
                            : "{\"userId\":7,\"status\":\"ACTIVE\",\"sagaId\":null}")
                        : "{\"message\":\"stub error\"}";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(code, bytes.length);
                try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
                finally { exchange.close(); }
            });
            stub.start();
            return stub;
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
