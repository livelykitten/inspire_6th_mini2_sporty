package com.example.sporty.features.facilities.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.example.sporty.features.facilities.domain.entity.LocationEntity;
import com.example.sporty.features.facilities.domain.entity.ServiceEntity;

import lombok.Builder;
import lombok.Getter;

/**
 * FC-02 체육시설 상세 화면에 반환하는 응답 DTO다.
 */
@Getter
@Builder
public class ServiceDetailResponseDto {

    /** 서비스 구분. service 테이블 PK */
    private Long id;
    /** 서비스 ID. 서울시 SVCID */
    private String serviceId;
    private String status;
    private String serviceName;
    private String paymentMethod;
    private String locationName;
    private String serviceType;
    private String region;
    private String contact;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private LocalTime startTime;
    private LocalTime endTime;
    private String url;
    private LocalDateTime reservationDeadlineAt;

    public static ServiceDetailResponseDto from(ServiceEntity service) {
        LocationEntity location = service.getLocation();

        return ServiceDetailResponseDto.builder()
                .id(service.getId())
                .serviceId(valueOrDefault(service.getServiceId()))
                .status(valueOrDefault(service.getStatus()))
                .serviceName(valueOrDefault(service.getName()))
                .paymentMethod(toPaymentMethod(service.getIsFree()))
                .locationName(valueOrDefault(location.getName()))
                .serviceType(valueOrDefault(location.getFacilityName()))
                .region(valueOrDefault(location.getRegion()))
                .contact(valueOrDefault(location.getContact()))
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .startTime(service.getStartTime())
                .endTime(service.getEndTime())
                .url(valueOrDefault(service.getUrl()))
                .reservationDeadlineAt(service.getReservationDeadlineAt())
                .build();
    }

    private static String valueOrDefault(String value) {
        return value == null || value.isBlank() ? "정보없음" : value;
    }

    private static String toPaymentMethod(Boolean isFree) {
        if (isFree == null) {
            return "정보없음";
        }
        return isFree ? "무료" : "유료";
    }
}
