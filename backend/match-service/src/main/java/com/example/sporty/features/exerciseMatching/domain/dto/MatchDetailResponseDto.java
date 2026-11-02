package com.example.sporty.features.exerciseMatching.domain.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;
import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * EM-03 운동 매칭 상세 화면에 필요한 응답 정보.
 * 장소 정보는 시설 코드 연동 전까지 null로 반환한다.
 */
@Builder
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MatchDetailResponseDto {

    private Long matchId;
    private String title;
    private String description;

    private LocalDateTime startAt;
    private LocalDateTime endAt;

    private Integer maxParticipant;
    private Integer currentParticipantCount;

    private MatchStatus status;
    private SkillLevel skillLevel;
    private SportType sportType;
    private GenderGroup genderGroup;

    private Long serviceId;
    private String serviceName;
    private String locationName;
    private String region;

    private List<MatchParticipantSummaryDto> participants;

    // 현재 조회 사용자 기준. 비로그인은 둘 다 false, OWNER는 둘 다 true다.
    @JsonProperty("isOwner")
    private Boolean isOwner;

    @JsonProperty("isParticipant")
    private Boolean isParticipant;
}
