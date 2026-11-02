package com.example.sporty.features.ai.domain.dto;

import java.time.LocalDateTime;

import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

// 매치 개설 폼에 채울 AI 초안
@Builder
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiDraftDto {

    private SportType sportType;
    private String region;
    private LocalDateTime startAt, endAt;
    private Integer maxParticipant;
    private SkillLevel skillLevel;
    private GenderGroup genderGroup;
    private String title, description;
}
