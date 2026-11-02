package com.example.sporty.security;

import com.example.sporty.features.commons.filter.UserIdHeaderAuthenticationFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;

class UserIdHeaderAuthenticationFilterTest {

    private final UserIdHeaderAuthenticationFilter filter =
            new UserIdHeaderAuthenticationFilter();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validHeaderCreatesLongPrincipal() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "7");
        var response = new MockHttpServletResponse();
        boolean[] chainCalled = {false};

        filter.doFilter(request, response, (req, res) -> {
            chainCalled[0] = true;

            var authentication =
                    SecurityContextHolder.getContext().getAuthentication();

            assertNotNull(authentication);
            assertTrue(authentication.isAuthenticated());
            assertInstanceOf(Long.class, authentication.getPrincipal());
            assertEquals(7L, authentication.getPrincipal());
            assertEquals("7", authentication.getName());
        });

        assertTrue(chainCalled[0]);
    }

    @Test
    void missingHeaderDoesNotAuthenticate() throws Exception {
        assertUnauthenticated(null);
    }

    @Test
    void invalidHeadersDoNotAuthenticate() throws Exception {
        for (String value : new String[]{
                "", "abc", "0", "-1", "1.5", " 7 ",
                "9223372036854775808"
        }) {
            assertUnauthenticated(value);
        }
    }

    private void assertUnauthenticated(String header) throws Exception {
        SecurityContextHolder.clearContext();

        var request = new MockHttpServletRequest();
        if (header != null) {
            request.addHeader("X-User-Id", header);
        }

        var response = new MockHttpServletResponse();
        boolean[] chainCalled = {false};

        filter.doFilter(request, response, (req, res) -> {
            chainCalled[0] = true;
            assertNull(
                    SecurityContextHolder.getContext().getAuthentication(),
                    "인증되면 안 되는 헤더: " + header
            );
        });

        assertTrue(chainCalled[0]);
    }
}