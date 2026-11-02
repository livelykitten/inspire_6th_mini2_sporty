package com.example.sporty.features.facilities.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.sporty.features.commons.exception.matches.ServiceNotFoundException;
import com.example.sporty.features.facilities.domain.dto.ServiceDetailResponseDto;
import com.example.sporty.features.facilities.domain.dto.ServiceRequestDto;
import com.example.sporty.features.facilities.domain.dto.ServiceResponseDto;
import com.example.sporty.features.facilities.domain.entity.ServiceEntity;
import com.example.sporty.features.facilities.repository.ServiceRepository;
import com.example.sporty.features.facilities.specification.ServiceSpecification;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityQueryService {

    private final ServiceRepository serviceRepository;

    public List<ServiceResponseDto> getFacilities(
            ServiceRequestDto request
    ) {
        validateTimeRange(request);

        List<ServiceEntity> services = serviceRepository.findAll(
                ServiceSpecification.search(request)
        );

        return services.stream()
                .map(ServiceResponseDto::from)
                .toList();
    }

    // Path의 serviceId는 service 테이블 PK다.
    public ServiceDetailResponseDto getFacility(Long id) {
        ServiceEntity service = serviceRepository.findByIdAndActiveTrue(id)
                .orElseThrow(ServiceNotFoundException::new);

        return ServiceDetailResponseDto.from(service);
    }

    private void validateTimeRange(ServiceRequestDto request) {
        if (request.getStartTime() != null
                && request.getEndTime() != null
                && request.getStartTime().isAfter(request.getEndTime())) {
            throw new IllegalArgumentException(
                    "시작시간은 종료시간보다 늦을 수 없습니다."
            );
        }
    }
}
