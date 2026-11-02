package com.example.sporty.features.exerciseMatching.domain.dto;

import com.example.sporty.features.exerciseMatching.domain.enums.MatchParticipantRole;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * EM-03 운동 매칭 상세 화면의 참가자 목록에 표시할 프로필 및 역할 정보.
 */
@Builder
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MatchParticipantSummaryDto {

    private Long profileId;
    private String nickname;
    private String imageUrl;
    private MatchParticipantRole role;
}
