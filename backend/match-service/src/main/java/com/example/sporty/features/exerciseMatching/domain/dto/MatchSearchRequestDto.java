package com.example.sporty.features.exerciseMatching.domain.dto;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;
import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Builder 
@Getter 
@Setter
@ToString 
@NoArgsConstructor 
@AllArgsConstructor 
@JsonIgnoreProperties (ignoreUnknown = true)
public class MatchSearchRequestDto {
    @Positive(message = "체육서비스 ID는 양수여야 합니다.")
    private Long serviceId;

    @Size(max = 50, message = "제목 검색어는 50자 이하여야 합니다.")
    private String titleKeyword;

    @Size(max = 500, message = "설명 검색어는 500자 이하여야 합니다.")
    private String descriptionKeyword;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startAt;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endAt;

    @Positive(message = "모집 정원은 1명 이상이어야 합니다.")
    private Integer maxParticipant;

    private SkillLevel skillLevel;
    private SportType sportType;
    private GenderGroup genderGroup;

    private String region;
    private MatchStatus status;
    private Boolean isFree;

    @JsonIgnore
    @AssertTrue(message = "종료 시간은 시작 시간보다 이후여야 합니다.")
    public boolean isTimeRangeValid() {
        return startAt == null || endAt == null || endAt.isAfter(startAt);
    }
}
