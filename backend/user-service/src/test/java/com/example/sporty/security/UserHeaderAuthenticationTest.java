package com.example.sporty.security;

import com.example.sporty.features.commons.config.SecurityConfig;
import com.example.sporty.features.users.controller.UserController;
import com.example.sporty.features.users.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserHeaderAuthenticationTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UserService userService;

    @Test
    void validHeaderPassesLongUserIdToController() throws Exception {
        mvc.perform(get("/api/users/me")
                        .header("X-User-Id", "7"))
                .andExpect(status().isOk());

        verify(userService).getMyInfo(7L);
    }

    @Test
    void missingHeaderReturns401() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "abc", "0", "-1", "1.5", " 7 ",
            "9223372036854775808"
    })
    void invalidHeaderReturns401(String header) throws Exception {
        mvc.perform(get("/api/users/me")
                        .header("X-User-Id", header))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @Test
    void authorizationHeaderAloneDoesNotAuthenticate() throws Exception {
        mvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer unverified-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }
}