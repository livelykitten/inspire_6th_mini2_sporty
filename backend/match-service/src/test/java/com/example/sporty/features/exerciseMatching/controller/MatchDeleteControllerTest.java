package com.example.sporty.features.exerciseMatching.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.sporty.features.commons.config.SecurityConfig;
import com.example.sporty.features.exerciseMatching.client.MemberLookup;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchEntity;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchParticipantEntity;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchParticipantRole;
import com.example.sporty.features.exerciseMatching.repository.MatchParticipantRepository;
import com.example.sporty.features.exerciseMatching.repository.MatchRepository;
import com.example.sporty.features.exerciseMatching.repository.FacilityServiceSnapshotRepository;
import com.example.sporty.features.exerciseMatching.service.MatchService;

// 실제 X-User-Id 필터, Controller, Service, 예외 처리기를 사용하고 DB만 대체한다.
@WebMvcTest(MatchController.class)
@Import({MatchService.class, SecurityConfig.class})
class MatchDeleteControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MatchRepository matchRepository;

    @MockitoBean
    private MatchParticipantRepository matchParticipantRepository;

    @MockitoBean
    private MemberLookup memberLookup;

    @MockitoBean
    private FacilityServiceSnapshotRepository facilityServiceSnapshotRepository;

    @Test
    @DisplayName("[EM05-001] 생성자는 참가 기록과 매치를 삭제하고 본문 없는 204를 받는다")
    void ownerCanDeleteMatch() throws Exception {
        MatchEntity match = givenMatch(101L);
        givenParticipant(101L, 1L, MatchParticipantRole.OWNER);

        mvc.perform(delete("/api/matches/101")
                        .header("X-User-Id", "1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        InOrder order = inOrder(matchParticipantRepository, matchRepository);
        order.verify(matchParticipantRepository).deleteAllByMatch_Id(101L);
        order.verify(matchRepository).delete(match);
        verifyNoInteractions(memberLookup);
    }

    @Test
    @DisplayName("[EM05-002] 없는 매치는 404이며 삭제를 실행하지 않는다")
    void missingMatchReturnsNotFound() throws Exception {
        when(matchRepository.findByIdForUpdate(99999L)).thenReturn(Optional.empty());

        mvc.perform(delete("/api/matches/99999")
                        .header("X-User-Id", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATCH_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("매치를 찾을 수 없습니다."));

        verify(matchRepository, never()).delete(any(MatchEntity.class));
        verifyNoInteractions(matchParticipantRepository, memberLookup);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("[EM05-003] 일반 참가자와 미참가자는 토큰에 OWNER가 있어도 403이다")
    void nonOwnerCannotDeleteMatch(boolean joined) throws Exception {
        givenMatch(101L);
        if (joined) {
            givenParticipant(101L, 2L, MatchParticipantRole.PARTICIPANT);
        } else {
            when(matchParticipantRepository.findByMatch_IdAndUserId(101L, 2L))
                    .thenReturn(Optional.empty());
        }
        mvc.perform(delete("/api/matches/101").header("X-User-Id", "2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MATCH_DELETE_FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("매치 생성자만 삭제할 수 있습니다."));

        verify(matchParticipantRepository, never()).deleteAllByMatch_Id(anyLong());
        verify(matchRepository, never()).delete(any(MatchEntity.class));
        verifyNoInteractions(memberLookup);
    }

    @Test
    @DisplayName("[EM05-004] 비로그인 요청은 DB 접근 전에 401로 차단한다")
    void anonymousRequestIsRejected() throws Exception {
        mvc.perform(delete("/api/matches/101")).andExpect(status().isUnauthorized());

        verifyNoInteractions(matchRepository, matchParticipantRepository, memberLookup);
    }

    @Test
    @DisplayName("잘못된 X-User-Id 헤더로 삭제할 수 없다")
    void invalidUserIdHeadersAreRejected() throws Exception {
        for (String userId : List.of("broken", "0", "-1", "9223372036854775808")) {
            mvc.perform(delete("/api/matches/101").header("X-User-Id", userId))
                    .andExpect(status().isUnauthorized());
        }

        verifyNoInteractions(matchRepository, matchParticipantRepository, memberLookup);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "9223372036854775808"})
    @DisplayName("Long으로 해석할 수 없는 매치 ID는 400이다")
    void malformedMatchIdIsRejected(String matchId) throws Exception {
        mvc.perform(delete("/api/matches/{matchId}", matchId)
                        .header("X-User-Id", "1"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(matchRepository, matchParticipantRepository, memberLookup);
    }

    @Test
    @DisplayName("Integer 범위를 넘는 매치와 사용자 ID도 삭제할 수 있다")
    void longIdsArePreserved() throws Exception {
        Long matchId = 2147483648L;
        Long userId = 2147483649L;
        MatchEntity match = givenMatch(matchId);
        givenParticipant(matchId, userId, MatchParticipantRole.OWNER);

        mvc.perform(delete("/api/matches/{matchId}", matchId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNoContent());

        verify(matchParticipantRepository).deleteAllByMatch_Id(matchId);
        verify(matchRepository).delete(match);
    }

    private MatchEntity givenMatch(Long matchId) {
        MatchEntity match = MatchEntity.builder().id(matchId).build();
        when(matchRepository.findByIdForUpdate(matchId)).thenReturn(Optional.of(match));
        return match;
    }

    private void givenParticipant(Long matchId, Long userId, MatchParticipantRole role) {
        when(matchParticipantRepository.findByMatch_IdAndUserId(matchId, userId))
                .thenReturn(Optional.of(MatchParticipantEntity.builder()
                        .userId(userId).role(role).build()));
    }
}
