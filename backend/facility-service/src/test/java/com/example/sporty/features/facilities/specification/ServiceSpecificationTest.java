package com.example.sporty.features.facilities.specification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.example.sporty.features.facilities.domain.dto.ServiceRequestDto;
import com.example.sporty.features.facilities.domain.entity.LocationEntity;
import com.example.sporty.features.facilities.domain.entity.ServiceEntity;
import com.example.sporty.features.facilities.repository.LocationRepository;
import com.example.sporty.features.facilities.repository.ServiceRepository;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.auto_quote_keyword=true"
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.ANY
)
class ServiceSpecificationTest {

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Test
    @DisplayName("지역·시설 종류·운영시간 검색 및 비활성 서비스 제외")
    void searchServices() {
        saveService(
                "ACTIVE-TENNIS",
                "성동구",
                "테니스장",
                LocalTime.of(7, 0),
                LocalTime.of(19, 0),
                true
        );

        saveService(
                "SHORT-TENNIS",
                "성동구",
                "테니스장",
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                true
        );

        saveService(
                "OTHER-REGION",
                "강남구",
                "테니스장",
                LocalTime.of(7, 0),
                LocalTime.of(19, 0),
                true
        );

        saveService(
                "INACTIVE-TENNIS",
                "성동구",
                "테니스장",
                LocalTime.of(7, 0),
                LocalTime.of(19, 0),
                false
        );

        ServiceRequestDto request = new ServiceRequestDto();
        request.setRegion("성동구");
        request.setServiceType("테니스장");
        request.setStartTime(LocalTime.of(8, 0));
        request.setEndTime(LocalTime.of(18, 0));

        List<ServiceEntity> result = serviceRepository.findAll(
                ServiceSpecification.search(request)
        );

        assertThat(result)
                .extracting(ServiceEntity::getServiceId)
                .containsExactly("ACTIVE-TENNIS");
    }

    private void saveService(
            String serviceId,
            String region,
            String facilityName,
            LocalTime startTime,
            LocalTime endTime,
            boolean active
    ) {
        LocationEntity location = locationRepository.save(
                LocationEntity.builder()
                        .region(region)
                        .name(region + " 테스트 시설")
                        .facilityName(facilityName)
                        .build()
        );

        ServiceEntity service = ServiceEntity.builder()
                .location(location)
                .serviceId(serviceId)
                .name(serviceId)
                .status("접수중")
                .startTime(startTime)
                .endTime(endTime)
                .build();

        if (!active) {
            service.deactivate();
        }

        serviceRepository.save(service);
    }
}
