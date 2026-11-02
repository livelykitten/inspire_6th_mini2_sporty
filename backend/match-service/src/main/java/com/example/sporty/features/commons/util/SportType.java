package com.example.sporty.features.commons.util;

import lombok.Getter;

@Getter
public enum SportType {
    SOCCER("축구"),
    FUTSAL("풋살"),
    BASKETBALL("농구"),
    BASEBALL("야구"),
    TENNIS("테니스"),
    BADMINTON("배드민턴"),
    TABLE_TENNIS("탁구"),
    VOLLEYBALL("배구"),
    SWIMMING("수영"),
    RUNNING("러닝");

    private final String description;

    SportType(String description) {
        this.description = description;
    }

    public static SportType fromFacilityType(String facilityType) {
    if (facilityType == null || facilityType.isBlank()) {
        throw new IllegalArgumentException("시설 유형이 비어 있습니다.");
    }

    return switch (facilityType.trim()) {
        case "축구장" -> SOCCER;
        case "풋살장" -> FUTSAL;
        case "농구장" -> BASKETBALL;
        case "야구장" -> BASEBALL;
        case "테니스장" -> TENNIS;
        case "배드민턴장" -> BADMINTON;
        case "탁구장" -> TABLE_TENNIS;
        case "배구장" -> VOLLEYBALL;
        case "수영장" -> SWIMMING;
        default -> throw new IllegalArgumentException(
                "지원하지 않는 시설 유형: " + facilityType);
    };
}

}
