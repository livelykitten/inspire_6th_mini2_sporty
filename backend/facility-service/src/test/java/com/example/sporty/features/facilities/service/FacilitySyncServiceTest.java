package com.example.sporty.features.facilities.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.sporty.features.facilities.domain.entity.FacilityOutbox;
import com.example.sporty.features.facilities.domain.entity.LocationEntity;
import com.example.sporty.features.facilities.domain.entity.ServiceEntity;
import com.example.sporty.features.facilities.repository.FacilityOutboxRepository;
import com.example.sporty.features.facilities.repository.LocationRepository;
import com.example.sporty.features.facilities.repository.ServiceRepository;
import com.example.sporty.features.facilities.service.FacilitySyncService.SyncResult;
import com.example.sporty.features.openapi.SeoulFacilityApiClient;
import com.example.sporty.features.openapi.domain.dto.SeoulFacilityResponseDto.Row;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class FacilitySyncServiceTest {

    @Mock
    private SeoulFacilityApiClient apiClient;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private FacilityOutboxRepository outboxRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private FacilitySyncService facilitySyncService;

    @BeforeEach
    void setUp() {
        facilitySyncService = new FacilitySyncService(
                apiClient,
                locationRepository,
                serviceRepository,
                outboxRepository,
                objectMapper,
                transactionTemplate
        );

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    @DisplayName(
            "서울시 응답 2행(신규 1 · 기존 매칭 1) + DB 기존 2행(그중 1행은 응답에 없음)"
                    + " 동기화 시 outbox 3건이 active = true, true, false 순으로 저장된다"
    )
    void syncAll_createsUpdatesAndDeactivatesServices() throws Exception {
        Row newRow = objectMapper.readValue("""
                {
                  "SVCID": "SVC-NEW",
                  "MINCLASSNM": "축구장",
                  "SVCNM": "새 축구장 예약",
                  "PLACENM": "새싹공원",
                  "AREANM": "강남구",
                  "SVCSTATNM": "접수중",
                  "PAYATNM": "무료",
                  "SVCURL": "https://example.com/new",
                  "RCPTENDDT": "2026-12-01 00:00:00",
                  "X": "127.1",
                  "Y": "37.5",
                  "TELNO": "02-111-2222",
                  "V_MIN": "09:00",
                  "V_MAX": "18:00"
                }
                """, Row.class);

        Row existingRow = objectMapper.readValue("""
                {
                  "SVCID": "SVC-EXIST",
                  "MINCLASSNM": "테니스장",
                  "SVCNM": "기존 테니스장 예약",
                  "PLACENM": "응봉공원",
                  "AREANM": "성동구",
                  "SVCSTATNM": "접수중",
                  "PAYATNM": "유료",
                  "SVCURL": "https://example.com/exist",
                  "RCPTENDDT": "2026-12-15 00:00:00",
                  "X": "127.2",
                  "Y": "37.6",
                  "TELNO": "02-333-4444",
                  "V_MIN": "07:00",
                  "V_MAX": "22:00"
                }
                """, Row.class);

        when(apiClient.fetchAll()).thenReturn(List.of(newRow, existingRow));
        when(locationRepository.findAll()).thenReturn(List.of());

        LocationEntity existingLocation = LocationEntity.builder()
                .region("성동구")
                .name("응봉공원")
                .facilityName("테니스장")
                .build();
        LocationEntity goneLocation = LocationEntity.builder()
                .region("마포구")
                .name("상암공원")
                .facilityName("풋살장")
                .build();

        ServiceEntity existingService = ServiceEntity.builder()
                .location(existingLocation)
                .serviceId("SVC-EXIST")
                .name("기존 테니스장 예약(갱신 전)")
                .build();
        ServiceEntity goneService = ServiceEntity.builder()
                .location(goneLocation)
                .serviceId("SVC-GONE")
                .name("사라진 풋살장 예약")
                .build();

        when(serviceRepository.findAll())
                .thenReturn(List.of(existingService, goneService));

        SyncResult result = facilitySyncService.syncAll();

        assertThat(result.fetchedCount()).isEqualTo(2);
        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.deactivatedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(0);

        ArgumentCaptor<FacilityOutbox> outboxCaptor =
                ArgumentCaptor.forClass(FacilityOutbox.class);
        verify(outboxRepository, times(3)).save(outboxCaptor.capture());

        List<Boolean> activeFlags = outboxCaptor.getAllValues().stream()
                .map(this::readActiveFlag)
                .toList();

        assertThat(activeFlags).containsExactly(true, true, false);
    }

    private boolean readActiveFlag(FacilityOutbox outbox) {
        try {
            JsonNode node = objectMapper.readTree(outbox.getPayload());
            return node.get("active").asBoolean();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
