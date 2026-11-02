package com.example.sporty.features.exerciseMatching.domain.dto;

import java.time.LocalDateTime;

import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;
import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Builder 
@Getter 
@ToString 
@NoArgsConstructor 
@AllArgsConstructor 
@JsonIgnoreProperties (ignoreUnknown = true)
public class MatchRequestDto {
    
    private Long matchId;
    private Long serviceId;
    private String title, description;
    
    private LocalDateTime startAt, endAt;

    private Integer maxParticipant;

    private MatchStatus status;

    private SkillLevel skillLevel;
    private SportType sportType;

    private String region;

    private GenderGroup genderGroup;
    
}
