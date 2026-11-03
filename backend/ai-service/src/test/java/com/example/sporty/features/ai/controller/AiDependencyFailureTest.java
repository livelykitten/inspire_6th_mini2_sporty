package com.example.sporty.features.ai.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.sporty.features.ai.agent.MatchAiAgent;
import com.example.sporty.features.ai.agent.MatchDraftAiAgent;
import com.example.sporty.features.ai.agent.MatchRecommendAiAgent;
import com.example.sporty.features.commons.handler.GlobalExceptionHandler;

import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;

class AiDependencyFailureTest {

    private MatchAiAgent searchAgent;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        searchAgent = mock(MatchAiAgent.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AiController(
                        searchAgent, mock(MatchDraftAiAgent.class), mock(MatchRecommendAiAgent.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/matches", "/api/matches/me", "/api/users/me"})
    void dependency503Returns503(String path) throws Exception {
        when(searchAgent.search(anyString())).thenThrow(upstreamError(503, path));

        mockMvc.perform(searchRequest())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_DEPENDENCY_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("잠시 후 다시 시도해주세요."));
    }

    @Test
    void timeoutReturns503() throws Exception {
        when(searchAgent.search(anyString())).thenThrow(new RetryableException(
                -1, "Read timed out", Request.HttpMethod.GET,
                new SocketTimeoutException(), (Long) null, request("/api/matches")));

        mockMvc.perform(searchRequest())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_DEPENDENCY_UNAVAILABLE"));
    }

    @Test
    void invalidMatchSearchRemains400() throws Exception {
        when(searchAgent.search(anyString())).thenThrow(upstreamError(400, "/api/matches"));

        mockMvc.perform(searchRequest()).andExpect(status().isBadRequest());
    }

    @Test
    void missingUserKeepsExisting404Contract() throws Exception {
        when(searchAgent.search(anyString())).thenThrow(upstreamError(404, "/api/users/me"));

        mockMvc.perform(searchRequest())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("존재하지 않는 회원입니다."));
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404, 429, 500, 502, 504})
    void unspecifiedUpstreamErrorDoesNotUseDependencyCode(int upstreamStatus) throws Exception {
        when(searchAgent.search(anyString())).thenThrow(upstreamError(upstreamStatus, "/api/matches/me"));

        mockMvc.perform(searchRequest())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(nullValue()));
    }

    private MockHttpServletRequestBuilder searchRequest() {
        return post("/api/ai/matches/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"강남 풋살\"}");
    }

    private Request request(String path) {
        return Request.create(Request.HttpMethod.GET, "http://localhost:18000" + path,
                Map.of(), (byte[]) null, StandardCharsets.UTF_8, null);
    }

    private FeignException upstreamError(int status, String path) {
        return FeignException.errorStatus("dependency", Response.builder()
                .status(status).reason("upstream error").request(request(path))
                .headers(Map.of()).body("upstream internal details", StandardCharsets.UTF_8).build());
    }
}
