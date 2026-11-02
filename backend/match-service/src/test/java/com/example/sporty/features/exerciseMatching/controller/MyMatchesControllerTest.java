package com.example.sporty.features.exerciseMatching.controller;

import com.example.sporty.features.exerciseMatching.service.MatchService;
import com.example.sporty.features.commons.config.SecurityConfig;
import com.example.sporty.features.commons.filter.JwtAuthenticationFilter;
import com.example.sporty.features.commons.token.JwtProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MatchController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = "jwt.secret=test-only-signing-secret-with-at-least-32-bytes")
class MyMatchesControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean MatchService service;

    @Test
    void rejectsAnonymousRequest() throws Exception {
        mvc.perform(get("/api/matches/me")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsInvalidTokenUnlikePublicDetail() throws Exception {
        mvc.perform(get("/api/matches/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void usesJwtIdInsteadOfQueryParameter() throws Exception {
        String token = new JwtProvider("test-only-signing-secret-with-at-least-32-bytes").createAccessToken(7L);
        when(service.getMyMatches(7L)).thenReturn(List.of());
        mvc.perform(get("/api/matches/me?userId=999").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(service).getMyMatches(7L);
        verify(service, never()).getMatchDetail(anyLong(), any());
    }
}
