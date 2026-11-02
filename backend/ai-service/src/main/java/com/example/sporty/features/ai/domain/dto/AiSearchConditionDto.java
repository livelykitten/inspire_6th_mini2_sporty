package com.example.sporty.features.ai.domain.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchSearchRequestDto;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;
import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

// AI가 문장에서 추출한 검색 조건 (tool → agent 내부 전달용)
@Builder
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiSearchConditionDto {

    private SportType sportType;
    private String region;
    private LocalDateTime startAt, endAt;
    private SkillLevel skillLevel;
    private GenderGroup genderGroup;
    private MatchStatus status;

    // MatchService.searchMatches()에 넘길 형태로 변환 (AI가 추출하는 필드만 채움)
    public MatchSearchRequestDto toSearchRequest() {
        return MatchSearchRequestDto.builder()
                .sportType(sportType).region(region)
                .startAt(startAt).endAt(endAt)
                .skillLevel(skillLevel).genderGroup(genderGroup).status(status)
                .build();
    }

    // 프론트 조건 요약용. 순서: 종목 → 성별 → 날짜 → 자치구 → 실력 수준 → 모집 상태, 값이 없는 항목은 넣지 않음
    public List<AiConditionDto> toConditions() {
        List<AiConditionDto> conditions = new ArrayList<>();
        if (sportType != null) {
            conditions.add(new AiConditionDto("종목", sportType.getDescription()));
        }
        if (genderGroup != null) {
            conditions.add(new AiConditionDto("성별", genderGroup.getDescription()));
        }
        String date = toDateText();
        if (date != null) {
            conditions.add(new AiConditionDto("날짜", date));
        }
        if (region != null && !region.isBlank()) {
            conditions.add(new AiConditionDto("자치구", region));
        }
        if (skillLevel != null) {
            conditions.add(new AiConditionDto("실력 수준", toSkillText(skillLevel)));
        }
        if (status != null) {
            conditions.add(new AiConditionDto("모집 상태", toStatusText(status)));
        }
        return conditions;
    }

    // 하루면 2026-10-03, 기간이면 2026-10-03 ~ 2026-10-04
    private String toDateText() {
        if (startAt == null && endAt == null) {
            return null;
        }
        if (startAt == null) {
            return "~ " + endAt.toLocalDate();
        }
        if (endAt == null || startAt.toLocalDate().equals(endAt.toLocalDate())) {
            return startAt.toLocalDate().toString();
        }
        return startAt.toLocalDate() + " ~ " + endAt.toLocalDate();
    }

    private static String toSkillText(SkillLevel skillLevel) {
        return switch (skillLevel) {
            case BEGINNER -> "초급";
            case INTERMEDIATE -> "중급";
            case ADVANCED -> "고급";
        };
    }

    private static String toStatusText(MatchStatus status) {
        return switch (status) {
            case RECRUITING -> "모집중";
            case CLOSED -> "마감";
        };
    }
}
