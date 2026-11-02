package com.example.sporty.features.exerciseMatching.domain.dto;

import java.time.LocalDateTime;

import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchEntity;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;
import com.example.sporty.features.facilities.domain.entity.ServiceEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchCreateRequestDto {
    @NotNull(message = "체육서비스 ID는 필수입니다.")
    @Positive(message = "체육서비스 ID는 양수여야 합니다.")
    private Long serviceId;

    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 50, message = "제목은 50자 이하여야 합니다.")
    private String title;

    @Size(max = 500, message = "설명은 500자 이하여야 합니다.")
    private String description;

    @NotNull(message = "시작 시간은 필수입니다.")
    private LocalDateTime startAt;

    @NotNull(message = "종료 시간은 필수입니다.")
    private LocalDateTime endAt;

    @NotNull(message = "모집 정원은 필수입니다.")
    @Min(value = 1, message = "모집 정원은 1명 이상이어야 합니다.")
    private Integer maxParticipant;

    @NotNull(message = "운동 수준은 필수입니다.")
    private SkillLevel skillLevel;

    @NotNull(message = "운동 종목은 필수입니다.")
    private SportType sportType;

    @NotNull(message = "성별 구성은 필수입니다.")
    private GenderGroup genderGroup;

    @JsonIgnore
    @AssertTrue(message = "종료 시간은 시작 시간보다 이후여야 합니다.")
    public boolean isTimeRangeValid() {
        // Missing values are reported by the field-level @NotNull constraints.
        return startAt == null || endAt == null || endAt.isAfter(startAt);
    }

    // MatchService validates this catalog ID before persistence.
    public MatchEntity toEntity(ServiceEntity service) {
        return MatchEntity.builder()
            .title(this.getTitle())
            .description(this.getDescription())
            .startAt(this.getStartAt())
            .endAt(this.getEndAt())
            .maxParticipant(this.getMaxParticipant())
            .skillLevel(this.getSkillLevel())
            .service(service)
            .sportType(this.getSportType())
            .genderGroup(this.getGenderGroup())
            .build();
    }
}
