package com.example.sporty.features.ai.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

// AI-01 응답: { "initialValues": { ... } }
@Builder
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AiDraftResponseDto {

    private AiDraftDto initialValues;
}
