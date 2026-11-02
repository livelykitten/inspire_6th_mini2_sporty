package com.example.sporty.features.exerciseMatching.domain.dto;

import java.time.LocalDateTime;
import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchEntity;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchParticipantEntity;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchParticipantRole;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
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

    public static MyMatchResponseDto toResponseDto(MatchParticipantEntity participant, long participantCount) {
        MatchEntity match = participant.getMatch();
        return MyMatchResponseDto.builder()
                .matchId(match.getId())
                .title(match.getTitle())
                .startAt(match.getStartAt())
                .endAt(match.getEndAt())
                .numCurrentParticipant(Math.toIntExact(participantCount))
                .maxParticipant(match.getMaxParticipant())
                .status(match.getStatus())
                .sportType(match.getSportType())
                .region(match.getService().getLocation().getRegion())
                .facilityName(match.getService().getName())
                .isFree(match.getService().getIsFree())
                .role(participant.getRole())
                .build();
    }
}
