package com.example.sporty.features.exerciseMatching.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.sporty.features.commons.config.SecurityConfig;
import com.example.sporty.features.commons.filter.JwtAuthenticationFilter;
import com.example.sporty.features.commons.token.JwtProvider;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchEntity;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchParticipantEntity;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchParticipantRole;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;
import com.example.sporty.features.exerciseMatching.repository.MatchParticipantRepository;
import com.example.sporty.features.exerciseMatching.repository.MatchRepository;
import com.example.sporty.features.exerciseMatching.service.MatchService;
import com.example.sporty.features.profiles.repository.ProfileRepository;
import com.example.sporty.features.users.domain.entity.UserEntity;
import com.example.sporty.features.users.repository.UserRepository;
import com.example.sporty.features.facilities.repository.ServiceRepository;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

// JWT 필터, Controller, Service, 예외 처리기를 사용하고 DB만 대체한다.
@WebMvcTest(MatchController.class)
@Import({MatchService.class, SecurityConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = "jwt.secret=test-only-signing-secret-with-at-least-32-bytes")
class MatchJoinControllerTest {

    private static final String SECRET = "test-only-signing-secret-with-at-least-32-bytes";
    private final JwtProvider jwtProvider = new JwtProvider(SECRET);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MatchRepository matchRepository;

    @MockitoBean
    private MatchParticipantRepository matchParticipantRepository;

    @MockitoBean
    private ProfileRepository profileRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private ServiceRepository serviceRepository;

    @Test
    @DisplayName("[EM06-001] 로그인 사용자를 PARTICIPANT로 저장하고 201과 참가 정보를 반환한다")
    void joinsWithAuthenticatedUserId() throws Exception {
        MatchEntity match = givenMatch(MatchStatus.RECRUITING, LocalDateTime.now().plusDays(1));
        when(matchParticipantRepository.countByMatch_Id(201L)).thenReturn(2L);
        givenSavedParticipant();

        mvc.perform(post("/api/matches/201/participants")
                        .header("Authorization", bearer(2L))
                        .contentType("application/json")
                        .content("{\"userId\":999,\"role\":\"OWNER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.matchParticipantId").value(31))
                .andExpect(jsonPath("$.matchId").value(201))
                .andExpect(jsonPath("$.userId").value(2))
                .andExpect(jsonPath("$.role").value("PARTICIPANT"));

        ArgumentCaptor<MatchParticipantEntity> captor = ArgumentCaptor.forClass(MatchParticipantEntity.class);
        verify(matchParticipantRepository).save(captor.capture());
        assertThat(captor.getValue().getMatch()).isSameAs(match);
        assertThat(captor.getValue().getUser().getId()).isEqualTo(2L);
        assertThat(captor.getValue().getUser()).isSameAs(userRepository.findById(2L).orElseThrow());
        assertThat(captor.getValue().getRole()).isEqualTo(MatchParticipantRole.PARTICIPANT);
        assertThat(match.getStatus()).isEqualTo(MatchStatus.RECRUITING);
        verifyNoInteractions(profileRepository);
    }

    @Test
    @DisplayName("마지막 참가자가 등록되면 OWNER를 포함한 정원에 도달하여 CLOSED로 바뀐다")
    void lastParticipantClosesRecruitment() throws Exception {
        MatchEntity match = givenMatch(MatchStatus.RECRUITING, LocalDateTime.now().plusDays(1));
        when(matchParticipantRepository.countByMatch_Id(201L)).thenReturn(4L);
        givenSavedParticipant();

        mvc.perform(post("/api/matches/201/participants").header("Authorization", bearer(2L)))
                .andExpect(status().isCreated());

        assertThat(match.getStatus()).isEqualTo(MatchStatus.CLOSED);
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 2L})
    @DisplayName("[EM06-002/003] 기존 참가자와 OWNER의 재참여는 마감 여부보다 먼저 409로 차단한다")
    void duplicateParticipationReturnsConflict(Long userId) throws Exception {
        MatchEntity match = givenMatch(MatchStatus.CLOSED, LocalDateTime.now().minusDays(1));
        when(matchParticipantRepository.existsByMatch_IdAndUserId(201L, userId)).thenReturn(true);

        mvc.perform(post("/api/matches/201/participants").header("Authorization", bearer(userId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATCH_ALREADY_JOINED"))
                .andExpect(jsonPath("$.message").value("이미 참가 중인 매치입니다."));

        verify(matchParticipantRepository, never()).save(any());
        assertThat(match.getStatus()).isEqualTo(MatchStatus.CLOSED);
    }

    @ParameterizedTest
    @ValueSource(longs = {5L, 6L})
    @DisplayName("[EM06-004] 정원이 가득 찼거나 초과한 매치의 참여는 400이며 상태를 변경하지 않는다")
    void fullMatchReturnsBadRequest(long count) throws Exception {
        MatchEntity match = givenMatch(MatchStatus.RECRUITING, LocalDateTime.now().plusDays(1));
        when(matchParticipantRepository.countByMatch_Id(201L)).thenReturn(count);

        mvc.perform(post("/api/matches/201/participants").header("Authorization", bearer(6L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MATCH_FULL"))
                .andExpect(jsonPath("$.message").value("매치 정원이 가득 찼습니다."));

        verify(matchParticipantRepository, never()).save(any());
        assertThat(match.getStatus()).isEqualTo(MatchStatus.RECRUITING);
    }

    @Test
    @DisplayName("[EM06-005] 모집 마감된 매치는 400으로 차단한다")
    void closedMatchReturnsBadRequest() throws Exception {
        givenMatch(MatchStatus.CLOSED, LocalDateTime.now().plusDays(1));

        mvc.perform(post("/api/matches/201/participants").header("Authorization", bearer(2L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MATCH_RECRUITMENT_CLOSED"))
                .andExpect(jsonPath("$.message").value("모집이 마감된 매치입니다."));

        verify(matchParticipantRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1})
    @DisplayName("시작 1초 전까지 허용하고, 시작 시각과 그 이후에는 자리가 남아도 400으로 차단한다")
    void startTimeBoundary(int secondsAfterStart) throws Exception {
        LocalDateTime startAt = LocalDateTime.of(2026, 9, 25, 19, 0);
        LocalDateTime now = startAt.plusSeconds(secondsAfterStart);
        givenMatch(MatchStatus.RECRUITING, startAt);
        if (secondsAfterStart < 0) {
            givenSavedParticipant();
        }

        try (MockedStatic<LocalDateTime> time = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            time.when(LocalDateTime::now).thenReturn(now);
            var result = mvc.perform(post("/api/matches/201/participants")
                    .header("Authorization", bearer(2L)));
            if (secondsAfterStart < 0) {
                result.andExpect(status().isCreated());
            } else {
                result.andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("MATCH_ALREADY_STARTED"))
                        .andExpect(jsonPath("$.message").value("이미 시작된 매치에는 참여할 수 없습니다."));
                verify(matchParticipantRepository, never()).save(any());
            }
        }
    }

    @Test
    @DisplayName("[EM06-006] 없는 매치는 404이며 참가자를 저장하지 않는다")
    void missingMatchReturnsNotFound() throws Exception {
        mvc.perform(post("/api/matches/99999/participants").header("Authorization", bearer(2L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATCH_NOT_FOUND"));

        verifyNoInteractions(matchParticipantRepository, profileRepository);
    }

    @Test
    @DisplayName("[EM06-007] 비로그인 요청은 DB 접근 전에 401로 차단한다")
    void anonymousRequestIsRejected() throws Exception {
        mvc.perform(post("/api/matches/201/participants")).andExpect(status().isUnauthorized());

        verifyNoInteractions(matchRepository, matchParticipantRepository, profileRepository);
    }

    @Test
    @DisplayName("잘못된 토큰, 만료 토큰, Refresh Token으로 참가할 수 없다")
    void invalidTokensAreRejected() throws Exception {
        String expired = Jwts.builder().setSubject("2").claim("tokenType", "ACCESS")
                .setExpiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        for (String token : List.of("broken", expired, jwtProvider.createRefreshToken(2L))) {
            mvc.perform(post("/api/matches/201/participants").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }

        verifyNoInteractions(matchRepository, matchParticipantRepository, profileRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "9223372036854775808"})
    @DisplayName("Long으로 해석할 수 없는 matchId는 400이다")
    void malformedMatchIdIsRejected(String matchId) throws Exception {
        mvc.perform(post("/api/matches/{matchId}/participants", matchId)
                        .header("Authorization", bearer(2L)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(matchRepository, matchParticipantRepository, profileRepository);
    }

    @Test
    @DisplayName("인증 토큰의 사용자가 DB에 없으면 401이며 참가 정보와 모집 상태를 변경하지 않는다")
    void missingUserDoesNotJoinOrCloseRecruitment() throws Exception {
        MatchEntity match = givenMatch(MatchStatus.RECRUITING, LocalDateTime.now().plusDays(1));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        mvc.perform(post("/api/matches/201/participants").header("Authorization", bearer(2L)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MATCH_USER_NOT_FOUND"));

        verifyNoInteractions(matchParticipantRepository);
        assertThat(match.getStatus()).isEqualTo(MatchStatus.RECRUITING);
    }

    private MatchEntity givenMatch(MatchStatus status, LocalDateTime startAt) {
        // 동일 ID 조회 시 같은 사용자 엔티티를 반환하여 저장된 연관관계도 검증한다.
        java.util.Map<Long, UserEntity> users = new java.util.HashMap<>();
        when(userRepository.findById(anyLong())).thenAnswer(invocation -> {
            Long userId = invocation.getArgument(0);
            return Optional.of(users.computeIfAbsent(userId, id -> UserEntity.builder().id(id).build()));
        });
        MatchEntity match = MatchEntity.builder().id(201L).maxParticipant(5)
                .startAt(startAt).status(status).build();
        when(matchRepository.findByIdForUpdate(201L)).thenReturn(Optional.of(match));
        return match;
    }

    private void givenSavedParticipant() {
        when(matchParticipantRepository.save(any(MatchParticipantEntity.class))).thenAnswer(invocation -> {
            MatchParticipantEntity participant = invocation.getArgument(0);
            return MatchParticipantEntity.builder().id(31L).match(participant.getMatch())
                    .user(participant.getUser()).role(participant.getRole()).build();
        });
    }

    private String bearer(Long userId) {
        return "Bearer " + jwtProvider.createAccessToken(userId);
    }
}
