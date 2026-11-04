package com.example.sporty.features.ai.client.dto;

import java.time.LocalDateTime;
import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.ai.client.enums.MatchParticipantRole;
import com.example.sporty.features.ai.client.enums.MatchStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

//내가 개설·참가한 매치 응답. 이후 matchId로 추천 후보에서 제외
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MyMatchResponseDto {
    private Long matchId;
    private String title;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer numCurrentParticipant;
    private Integer maxParticipant;
    private MatchStatus status;
    private SportType sportType;
    private String region;
    private String facilityName;
    @JsonProperty("isFree")
    private Boolean isFree;
    private MatchParticipantRole role;

}
