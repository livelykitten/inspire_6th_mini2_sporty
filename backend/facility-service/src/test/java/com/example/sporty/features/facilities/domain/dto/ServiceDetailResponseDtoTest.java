package com.example.sporty.features.facilities.domain.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.sporty.features.facilities.domain.entity.LocationEntity;
import com.example.sporty.features.facilities.domain.entity.ServiceEntity;

class ServiceDetailResponseDtoTest {

    @Test
    @DisplayName("FC-02: 상세 정보의 DB ID와 서울시 서비스 ID를 구분한다")
    void mapsServiceIdentifiersAndMissingValues() {
        LocationEntity location = LocationEntity.builder()
                .region("성동구")
                .name("응봉공원")
                .facilityName("테니스장")
                .build();
        ServiceEntity service = ServiceEntity.builder()
                .location(location)
                .serviceId("S251121100349891778")
                .name("응봉공원 테니스장")
                .build();
        ReflectionTestUtils.setField(service, "id", 1L);

        ServiceDetailResponseDto response =
                ServiceDetailResponseDto.from(service);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getServiceId())
                .isEqualTo("S251121100349891778");
        assertThat(response.getStatus()).isEqualTo("정보없음");
        assertThat(response.getPaymentMethod()).isEqualTo("정보없음");
        assertThat(response.getContact()).isEqualTo("정보없음");
        assertThat(response.getUrl()).isEqualTo("정보없음");
    }
}
