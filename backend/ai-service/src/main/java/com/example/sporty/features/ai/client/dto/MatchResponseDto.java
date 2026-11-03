package com.example.sporty.features.ai.client.dto;

import java.time.LocalDateTime;

import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.ai.client.enums.GenderGroup;
import com.example.sporty.features.ai.client.enums.MatchStatus;
import com.example.sporty.features.ai.client.enums.SkillLevel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

//match 서비스에서 받아올 정보
@Builder
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties (ignoreUnknown = true)
public class MatchResponseDto {

    private Long matchId;

    private String title;
    private String description;

    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private LocalDateTime reservationDeadlineAt;

    private Integer numCurrentParticipant;
    private Integer maxParticipant;
    private Double distance;

    private MatchStatus status;
    private SkillLevel skillLevel;
    private SportType sportType;

    private GenderGroup genderGroup;
    private String region;
    @JsonProperty("isFree")
    private Boolean isFree;

}
