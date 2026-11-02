package com.example.sporty.features.exerciseMatching.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.junit.jupiter.params.provider.CsvSource;
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

// 실제 JWT 필터 -> Controller -> Service -> 오류 응답을 검증하며 Repository만 대체한다.
@WebMvcTest(MatchController.class)
@Import({MatchService.class, SecurityConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = "jwt.secret=test-only-signing-secret-with-at-least-32-bytes")
class MatchLeaveControllerTest {

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

    @ParameterizedTest
    @CsvSource({"1,RECRUITING", "-1,CLOSED"})
    @DisplayName("[EM07-001, EM07-002] 일반 참가자는 경기 시작 전후에 탈퇴하고 빈 204 응답을 받는다")
    void participantCanLeaveBeforeAndAfterStart(int startOffsetHours, MatchStatus matchStatus) throws Exception {
        MatchEntity match = MatchEntity.builder().id(301L)
                .startAt(LocalDateTime.now().plusHours(startOffsetHours)).status(matchStatus).build();
        MatchParticipantEntity participant = MatchParticipantEntity.builder()
                .id(10L).match(match).user(UserEntity.builder().id(2L).build()).role(MatchParticipantRole.PARTICIPANT).build();
        when(matchRepository.findByIdForUpdate(301L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatch_IdAndUserId(301L, 2L))
                .thenReturn(Optional.of(participant));

        // 요청 파라미터로 다른 userId를 보내도 JWT에 있는 본인의 참가 정보만 삭제한다.
        mvc.perform(delete("/api/matches/301/participants/me").param("userId", "1")
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(2L)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(matchRepository).findByIdForUpdate(301L);
        verify(matchParticipantRepository).findByMatch_IdAndUserId(301L, 2L);
        verify(matchParticipantRepository).delete(participant);
        verifyNoMoreInteractions(matchRepository, matchParticipantRepository);
        verifyNoInteractions(profileRepository);
    }

    @Test
    @DisplayName("[EM07-003, EM07-007] OWNER 탈퇴는 403으로 차단하고 이후 매치 삭제는 204를 반환한다")
    void ownerCannotLeave() throws Exception {
        when(matchRepository.findByIdForUpdate(303L)).thenReturn(Optional.of(MatchEntity.builder().id(303L).build()));
        when(matchParticipantRepository.findByMatch_IdAndUserId(303L, 1L))
                .thenReturn(Optional.of(MatchParticipantEntity.builder()
                        .id(11L).user(UserEntity.builder().id(1L).build()).role(MatchParticipantRole.OWNER).build()));

        mvc.perform(delete("/api/matches/303/participants/me")
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(1L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MATCH_OWNER_CANNOT_LEAVE"))
                .andExpect(jsonPath("$.message").value("매치 생성자는 탈퇴할 수 없습니다. 매치 삭제 기능을 이용해주세요."));

        verify(matchRepository).findByIdForUpdate(303L);
        verify(matchParticipantRepository).findByMatch_IdAndUserId(303L, 1L);
        verifyNoMoreInteractions(matchRepository, matchParticipantRepository);

        MatchEntity match = MatchEntity.builder().id(303L).build();
        when(matchRepository.findById(303L)).thenReturn(Optional.of(match));
        mvc.perform(delete("/api/matches/303")
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(1L)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(matchParticipantRepository).deleteAllByMatch_Id(303L);
        verify(matchRepository).delete(match);
    }

    @Test
    @DisplayName("[EM07-004] 미참여자는 404를 받고 기존 참가 정보는 유지된다")
    void nonParticipantGetsNotFound() throws Exception {
        when(matchRepository.findByIdForUpdate(304L)).thenReturn(Optional.of(MatchEntity.builder().id(304L).build()));
        when(matchParticipantRepository.findByMatch_IdAndUserId(304L, 5L)).thenReturn(Optional.empty());

        mvc.perform(delete("/api/matches/304/participants/me")
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(5L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATCH_PARTICIPANT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("매치 참가 정보를 찾을 수 없습니다."));

        verify(matchRepository).findByIdForUpdate(304L);
        verify(matchParticipantRepository).findByMatch_IdAndUserId(304L, 5L);
        verifyNoMoreInteractions(matchRepository, matchParticipantRepository);
    }

    @Test
    @DisplayName("[EM07-005] 없는 매치 탈퇴는 404를 반환한다")
    void missingMatchGetsNotFound() throws Exception {
        when(matchRepository.findByIdForUpdate(99999L)).thenReturn(Optional.empty());

        mvc.perform(delete("/api/matches/99999/participants/me")
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(2L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATCH_NOT_FOUND"));

        verify(matchRepository).findByIdForUpdate(99999L);
        verifyNoMoreInteractions(matchRepository);
        verifyNoInteractions(matchParticipantRepository, profileRepository);
    }

    @Test
    @DisplayName("[EM07-006] 비로그인 탈퇴는 401을 반환하고 DB에 접근하지 않는다")
    void anonymousUserCannotLeave() throws Exception {
        mvc.perform(delete("/api/matches/301/participants/me"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(matchRepository, matchParticipantRepository, profileRepository);
    }

    @Test
    @DisplayName("잘못된 토큰, 만료 토큰, Refresh Token으로 탈퇴할 수 없다")
    void invalidTokensCannotLeave() throws Exception {
        String expiredToken = Jwts.builder().setSubject("2").claim("tokenType", "ACCESS")
                .setExpiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        for (String token : List.of("broken", expiredToken, jwtProvider.createRefreshToken(2L))) {
            mvc.perform(delete("/api/matches/301/participants/me")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }

        verifyNoInteractions(matchRepository, matchParticipantRepository, profileRepository);
    }
}
