package com.example.sporty.security;

import com.example.sporty.features.auth.controller.AuthController;
import com.example.sporty.features.auth.service.AuthService;
import com.example.sporty.features.auth.service.RefreshTokenService;
import com.example.sporty.features.commons.config.SecurityConfig;
import com.example.sporty.features.commons.filter.JwtAuthenticationFilter;
import com.example.sporty.features.commons.token.JwtProvider;
import com.example.sporty.features.users.repository.UserRepository;
import com.example.sporty.features.users.domain.entity.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({AuthService.class, JwtProvider.class, SecurityConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = "jwt.secret=test-only-signing-secret-with-at-least-32-bytes")
class TokenRefreshTest {
    static final String SECRET = "test-only-signing-secret-with-at-least-32-bytes";
    @Autowired MockMvc mvc;
    @Autowired JwtProvider tokens;
    @MockitoBean UserRepository users;
    @MockitoBean RefreshTokenService refreshTokens;

    @Test
    void refreshesWithMatchingRedisTokenEvenWhenAuthorizationIsExpired() throws Exception {
        String rt = tokens.createRefreshToken(1L);
        when(refreshTokens.findByUserId(1L)).thenReturn(rt);
        when(users.findById(1L)).thenReturn(Optional.of(UserEntity.builder().id(1L).status(UserStatus.ACTIVE).build()));
        mvc.perform(post("/api/auth/refresh").header("Authorization", "Bearer invalid-old-at")
                .contentType("application/json").content("{\"refreshToken\":\"" + rt + "\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.accessToken").isString()).andExpect(jsonPath("$.refreshToken").doesNotExist());
        verify(refreshTokens, never()).save(anyLong(), anyString(), anyLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"refreshToken\":null}", "{\"refreshToken\":\"\"}", "{\"refreshToken\":\"bad-token\"}"})
    void rejectsMissingAndMalformedTokens(String body) throws Exception {
        mvc.perform(post("/api/auth/refresh").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        verifyNoInteractions(refreshTokens, users);
    }

    @Test
    void rejectsAccessToken() throws Exception { reject(tokens.createAccessToken(1L)); }

    @Test
    void rejectsExpiredToken() throws Exception {
        reject(Jwts.builder().setSubject("1").claim("tokenType", "REFRESH")
                .setExpiration(new Date(System.currentTimeMillis() - 60000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact());
    }

    @Test
    void rejectsWrongSignature() throws Exception {
        reject(new JwtProvider("another-test-signing-secret-with-at-least-32-bytes").createRefreshToken(1L));
    }

    @Test
    void rejectsMissingRedisToken() throws Exception { reject(tokens.createRefreshToken(1L)); }

    @Test
    void rejectsReplacedRedisToken() throws Exception {
        when(refreshTokens.findByUserId(1L)).thenReturn(tokens.createRefreshToken(1L));
        reject(tokens.createRefreshToken(1L));
    }

    @Test
    void rejectsWithdrawnUser() throws Exception {
        String rt = tokens.createRefreshToken(1L);
        when(refreshTokens.findByUserId(1L)).thenReturn(rt);
        when(users.findById(1L)).thenReturn(Optional.of(UserEntity.builder().id(1L).status(UserStatus.WITHDRAWN).build()));
        reject(rt);
    }

    @Test
    void doesNotConvertRedisOutageToInvalidToken() {
        String rt = tokens.createRefreshToken(1L);
        when(refreshTokens.findByUserId(1L)).thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("test outage"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                mvc.perform(post("/api/auth/refresh").contentType("application/json")
                        .content("{\"refreshToken\":\"" + rt + "\"}")))
                .hasRootCauseInstanceOf(org.springframework.data.redis.RedisConnectionFailureException.class);
    }

    private void reject(String token) throws Exception {
        mvc.perform(post("/api/auth/refresh").contentType("application/json")
                .content("{\"refreshToken\":\"" + token + "\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }
}
