package com.example.sporty.features.facilities.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.example.sporty.features.commons.handler.ErrorResponse;
import com.example.sporty.features.facilities.domain.dto.ServiceDetailResponseDto;
import com.example.sporty.features.facilities.domain.dto.ServiceRequestDto;
import com.example.sporty.features.facilities.domain.dto.ServiceResponseDto;
import com.example.sporty.features.facilities.service.FacilityQueryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class FacilityServiceController {

    private final FacilityQueryService facilityQueryService;

    // FC-01: 체육시설 목록 조회
    @GetMapping
    public ResponseEntity<List<ServiceResponseDto>> getFacilities(
            @ModelAttribute ServiceRequestDto request
    ) {
        return ResponseEntity.ok(
                facilityQueryService.getFacilities(request)
        );
    }

    // FC-02: 체육시설 상세 정보 조회. Path의 serviceId는 DB PK다.
    @GetMapping("/{serviceId}")
    public ResponseEntity<ServiceDetailResponseDto> getFacility(
            @PathVariable Long serviceId
    ) {
        return ResponseEntity.ok(
                facilityQueryService.getFacility(serviceId)
        );
    }

    // 시간 형식이 틀린 검색 조건은 400으로 안내한다.
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCondition(BindException e) {
        return ResponseEntity.badRequest()
            .body(ErrorResponse.builder()
                    .code("INVALID_SEARCH_CONDITION")
                    .message("검색 조건 형식이 올바르지 않습니다. 시간은 HH:mm 형식으로 입력해주세요.")
                    .build());
    }

    // 숫자가 아닌 서비스 ID는 400으로 안내한다.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleInvalidServiceId(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
            .body(ErrorResponse.builder()
                    .code("INVALID_SERVICE_ID")
                    .message("서비스 ID 형식이 올바르지 않습니다. 숫자로 입력해주세요.")
                    .build());
    }
}
