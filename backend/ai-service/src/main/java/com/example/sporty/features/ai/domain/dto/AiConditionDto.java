package com.example.sporty.features.ai.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

// 프론트 조건 요약 한 줄. 예: { "label": "종목", "value": "풋살" }
@Builder
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AiConditionDto {

    private String label;
    private String value;
}
