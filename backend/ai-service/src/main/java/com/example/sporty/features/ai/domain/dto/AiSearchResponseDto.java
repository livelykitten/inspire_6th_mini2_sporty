package com.example.sporty.features.ai.domain.dto;

import java.util.List;

import com.example.sporty.features.exerciseMatching.domain.dto.MatchResponseDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

// AI-02 응답: AI가 해석한 조건 요약 + 조건에 맞는 매치 목록 + 직접 수정용 원래 조건
@Builder
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AiSearchResponseDto {

    private List<AiConditionDto> conditions;
    private List<MatchResponseDto> matches;
    // 조건 직접 수정 폼의 초기값. AI가 추출한 enum·일시 원래 값
    private AiSearchConditionDto criteria;
}
