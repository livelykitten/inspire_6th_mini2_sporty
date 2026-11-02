package com.example.sporty.features.facilities.domain.dto;

import java.time.LocalTime;

import org.springframework.format.annotation.DateTimeFormat;

import lombok.Getter;
import lombok.Setter;

/*
 FC-01 체육시설 목록 검색 조건이다.
 */
@Getter
@Setter
public class ServiceRequestDto {

    private String region;
    private String serviceName;
    private String serviceType;
    private String status;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime startTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime endTime;
}
