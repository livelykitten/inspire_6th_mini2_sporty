package com.example.sporty.features.exerciseMatching.domain.dto;

import com.example.sporty.features.exerciseMatching.domain.enums.MatchParticipantRole;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * EM-06 운동 매칭 참여 성공 시 반환하는 참가 정보.
 */
@Builder
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MatchParticipantResponseDto {

    private Long matchParticipantId;
    private Long matchId;
    private Long userId;
    private MatchParticipantRole role;
}
