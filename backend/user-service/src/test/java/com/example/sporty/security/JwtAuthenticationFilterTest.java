package com.example.sporty.security;

import com.example.sporty.features.commons.filter.JwtAuthenticationFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.ServletException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthenticationFilterTest {
    private static final String SECRET = "test-only-signing-secret-with-at-least-32-bytes";
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setup() {
        SecurityContextHolder.clearContext();
        filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "secret", SECRET);
        ReflectionTestUtils.invokeMethod(filter, "init");
    }

    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); }

    private MockHttpServletRequest request(String subject) {
        return request(subject, "ACCESS", new Date(System.currentTimeMillis() + 300000));
    }

    private MockHttpServletRequest request(String subject, String tokenType, Date expiration) {
        var builder = Jwts.builder().claim("role", "USER")
                .claim("tokenType", tokenType)
                .setExpiration(expiration);
        if (subject != null) builder.setSubject(subject);
        String token = builder.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        var request = new MockHttpServletRequest("POST", "/api/matches");
        request.setServletPath("/api/matches");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "2147483648", "9223372036854775807"})
    void validTokenSetsPrincipalAndRole(String subject) throws Exception {
        var chainCalled = new AtomicBoolean(false);
        var response = new MockHttpServletResponse();
        filter.doFilter(request(subject), response, (req, res) -> {
            chainCalled.set(true);
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertNotNull(authentication);
            assertTrue(authentication.isAuthenticated());
            assertEquals(Long.valueOf(subject), authentication.getPrincipal());
            assertTrue(authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        });
        assertTrue(chainCalled.get(), "Valid JWT must reach downstream");
        assertEquals(200, response.getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"member@example.com", "not-a-number", "0", "-1", "9223372036854775808"})
    void invalidUserIdMustNotAuthenticate(String subject) throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(request(subject), response, (req, res) -> fail("Invalid user ID reached downstream"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(401, response.getStatus());
    }

    @Test
    void tokenWithoutSubjectMustNotAuthenticate() throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(request(null), response, (req, res) -> fail("Invalid JWT reached downstream"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(401, response.getStatus());
    }

    @Test
    void blankSubjectMustNotAuthenticate() throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(request("   "), response, (req, res) -> fail("Invalid JWT reached downstream"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(401, response.getStatus());
    }

    @Test
    void tokenWithoutExpirationMustNotAuthenticate() throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(request("1", "ACCESS", null), response,
                (req, res) -> fail("JWT without expiration reached downstream"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(401, response.getStatus());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"REFRESH", "UNKNOWN"})
    void nonAccessTokenMustNotAuthenticate(String tokenType) throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(request("1", tokenType, new Date(System.currentTimeMillis() + 300000)), response,
                (req, res) -> fail("Non-access token reached downstream"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(401, response.getStatus());
    }

    @Test
    void downstreamFailureMustNotBecomeAuthenticationFailure() {
        var response = new MockHttpServletResponse();
        assertThrows(ServletException.class, () -> filter.doFilter(request("1"), response,
                (req, res) -> { throw new ServletException("downstream failure"); }));
    }
}
