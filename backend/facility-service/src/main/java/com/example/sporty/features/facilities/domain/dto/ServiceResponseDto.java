package com.example.sporty.features.facilities.domain.dto;

import java.time.LocalDateTime;
import java.time.LocalTime;

import com.example.sporty.features.facilities.domain.entity.LocationEntity;
import com.example.sporty.features.facilities.domain.entity.ServiceEntity;

import lombok.Builder;
import lombok.Getter;

/**
 * FC-01 체육시설 목록 화면에 반환하는 응답 DTO다.
 */
@Getter
@Builder
public class ServiceResponseDto {

    private Long serviceId;
    private String serviceName;
    private String serviceType;
    private String region;
    private String locationName;
    private String contact;
    private String status;
    private LocalTime startTime;
    private LocalTime endTime;
    private Boolean isFree;
    private String url;
    private LocalDateTime reservationDeadlineAt;

    public static ServiceResponseDto from(ServiceEntity service) {
        LocationEntity location = service.getLocation();

        return ServiceResponseDto.builder()
                .serviceId(service.getId())
                .serviceName(service.getName())
                .serviceType(location.getFacilityName())
                .region(location.getRegion())
                .locationName(location.getName())
                .contact(location.getContact())
                .status(service.getStatus())
                .startTime(service.getStartTime())
                .endTime(service.getEndTime())
                .isFree(service.getIsFree())
                .url(service.getUrl())
                .reservationDeadlineAt(service.getReservationDeadlineAt())
                .build();
    }
}
