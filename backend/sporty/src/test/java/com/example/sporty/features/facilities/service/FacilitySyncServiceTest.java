package com.example.sporty.features.facilities.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.sporty.features.facilities.domain.entity.ServiceEntity;
import com.example.sporty.features.facilities.repository.LocationRepository;
import com.example.sporty.features.facilities.repository.ServiceRepository;
import com.example.sporty.features.facilities.service.FacilitySyncService.SyncResult;
import com.example.sporty.features.openapi.SeoulFacilityApiClient;
import com.example.sporty.features.openapi.domain.dto.SeoulFacilityResponseDto.Row;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.auto_quote_keyword=true",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class FacilitySyncServiceTest {

    @Autowired private LocationRepository locations;
    @Autowired private ServiceRepository services;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;

    private final ObjectMapper mapper = new ObjectMapper();
    private SeoulFacilityApiClient apiClient;
    private FacilitySyncService syncService;

    @BeforeEach
    void setUp() {
        apiClient = mock(SeoulFacilityApiClient.class);
        // 실제 Repository와 트랜잭션을 사용하며 스케줄러/API 클라이언트 빈은 로드하지 않는다.
        syncService = new FacilitySyncService(apiClient, locations, services,
                new TransactionTemplate(transactionManager));
    }

    @Test
    @DisplayName("신규 서비스와 장소를 저장하고 API 필드를 변환한다")
    void createsLocationAndService() {
        assertThat(sync(row("A"))).isEqualTo(new SyncResult(1, 1, 0, 0, 0));

        ServiceEntity saved = service("A");
        assertThat(services.count()).isEqualTo(1);
        assertThat(locations.count()).isEqualTo(1);
        assertThat(saved.getName()).isEqualTo("테니스 예약");
        assertThat(saved.getStatus()).isEqualTo("접수중");
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(7, 0));
        assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(22, 0));
        assertThat(saved.getIsFree()).isTrue();
        assertThat(saved.getUrl()).isEqualTo("https://example.com/reservation");
        assertThat(saved.getReservationDeadlineAt()).isEqualTo(LocalDateTime.of(2026, 12, 31, 17, 0));
        assertThat(saved.getLocation().getRegion()).isEqualTo("성동구");
        assertThat(saved.getLocation().getName()).isEqualTo("응봉공원");
        assertThat(saved.getLocation().getFacilityName()).isEqualTo("테니스장");
        assertThat(saved.getLocation().getContact()).isEqualTo("02-1234-5678");
        assertThat(saved.getLocation().getLatitude()).isEqualByComparingTo("37.55");
        assertThat(saved.getLocation().getLongitude()).isEqualByComparingTo("127.03");
        verify(apiClient).fetchAll();
    }

    @Test
    @DisplayName("같은 외부 ID는 기존 DB ID를 유지하며 서비스와 장소를 갱신한다")
    void updatesExistingService() {
        sync(row("A"));
        Long serviceId = service("A").getId();
        Long locationId = service("A").getLocation().getId();
        Map<String, String> changed = fields("A");
        changed.putAll(Map.of("SVCNM", "변경된 예약", "SVCSTATNM", "접수마감",
                "V_MIN", "09:00", "V_MAX", "24:00", "PAYATNM", "유료",
                "SVCURL", "https://example.com/updated", "RCPTENDDT", "2027-01-01 18:00:00.0",
                "TELNO", "02-9999-9999", "Y", "37.56", "X", "127.04"));

        assertThat(sync(mapper.convertValue(changed, Row.class))).isEqualTo(new SyncResult(1, 0, 1, 0, 0));

        ServiceEntity updated = service("A");
        assertThat(services.count()).isEqualTo(1);
        assertThat(locations.count()).isEqualTo(1);
        assertThat(updated.getId()).isEqualTo(serviceId);
        assertThat(updated.getLocation().getId()).isEqualTo(locationId);
        assertThat(updated.getName()).isEqualTo("변경된 예약");
        assertThat(updated.getStatus()).isEqualTo("접수마감");
        assertThat(updated.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(updated.getEndTime()).isEqualTo(LocalTime.of(23, 59, 59));
        assertThat(updated.getIsFree()).isFalse();
        assertThat(updated.getUrl()).isEqualTo("https://example.com/updated");
        assertThat(updated.getReservationDeadlineAt()).isEqualTo(LocalDateTime.of(2027, 1, 1, 18, 0));
        assertThat(updated.getLocation().getContact()).isEqualTo("02-9999-9999");
        assertThat(updated.getLocation().getLatitude()).isEqualByComparingTo("37.56");
        assertThat(updated.getLocation().getLongitude()).isEqualByComparingTo("127.04");
    }

    @Test
    @DisplayName("동일 응답을 두 번 동기화해도 서비스와 장소가 중복되지 않는다")
    void repeatedSyncDoesNotDuplicateRows() {
        sync(row("A"), row("B"));
        List<Long> originalIds = services.findAll().stream().map(ServiceEntity::getId).toList();

        assertThat(sync(row("A"), row("B"))).isEqualTo(new SyncResult(2, 0, 2, 0, 0));
        assertThat(services.findAll()).extracting(ServiceEntity::getId)
                .containsExactlyInAnyOrderElementsOf(originalIds);
        assertThat(locations.count()).isEqualTo(1);
        verify(apiClient, times(2)).fetchAll();
    }

    @Test
    @DisplayName("응답에서 사라진 서비스만 비활성화하고 행은 보존한다")
    void deactivatesMissingService() {
        sync(row("A"), row("B"));
        Long missingId = service("B").getId();

        assertThat(sync(row("A"))).isEqualTo(new SyncResult(1, 0, 1, 1, 0));
        assertThat(service("A").isActive()).isTrue();
        assertThat(service("B").isActive()).isFalse();
        assertThat(service("B").getId()).isEqualTo(missingId);
        assertThat(services.count()).isEqualTo(2);
        assertThat(sync(row("A")).deactivatedCount()).isZero();
    }

    @ParameterizedTest(name = "필수값 {0}={1}이면 저장하지 않는다")
    @MethodSource("missingRequiredFields")
    void skipsRowsWithMissingRequiredFields(String field, String value) {
        Map<String, String> invalid = fields("INVALID");
        invalid.put("PLACENM", "저장되면 안 되는 장소");
        invalid.put(field, value);

        assertThat(sync(row("VALID"), mapper.convertValue(invalid, Row.class)))
                .isEqualTo(new SyncResult(2, 1, 0, 0, 1));
        assertThat(services.findAll()).extracting(ServiceEntity::getServiceId).containsExactly("VALID");
        assertThat(locations.count()).isEqualTo(1);
    }

    static Stream<Arguments> missingRequiredFields() {
        return Stream.of("SVCID", "SVCNM", "AREANM", "PLACENM", "MINCLASSNM")
                .flatMap(field -> Stream.of(null, "", "   ")
                        .map(value -> Arguments.of(field, value)));
    }

    @Test
    @DisplayName("비활성 서비스가 다시 나타나면 같은 DB ID로 재활성화한다")
    void reactivatesReturningService() {
        sync(row("A"), row("B"));
        Long returningId = service("B").getId();
        sync(row("A"));
        assertThat(service("B").isActive()).isFalse();

        assertThat(sync(row("A"), row("B"))).isEqualTo(new SyncResult(2, 0, 2, 0, 0));
        assertThat(service("B").isActive()).isTrue();
        assertThat(service("B").getId()).isEqualTo(returningId);
        assertThat(services.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("빈 응답이면 동기화를 중단하고 기존 활성 상태를 유지한다")
    void emptyResponsePreservesExistingData() {
        sync(row("A"));
        when(apiClient.fetchAll()).thenReturn(List.of());

        assertThatThrownBy(syncService::syncAll).isInstanceOf(IllegalStateException.class);
        flushAndClear();
        assertThat(service("A").isActive()).isTrue();
        assertThat(services.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("현재 동작 기록: 기존 ID의 필수값이 누락되면 skip 후 비활성화된다")
    void currentlyDeactivatesExistingServiceWhenItsRowIsInvalid() {
        // 바람직한 정책을 정의하는 테스트가 아니라, 확인이 필요한 현재 동작의 재현이다.
        sync(row("A"));
        Map<String, String> invalid = fields("A");
        invalid.put("SVCNM", null);

        assertThat(sync(mapper.convertValue(invalid, Row.class)))
                .isEqualTo(new SyncResult(1, 0, 0, 1, 1));
        assertThat(service("A").isActive()).isFalse();
        assertThat(service("A").getName()).isEqualTo("테니스 예약");
        assertThat(services.count()).isEqualTo(1);
    }

    private SyncResult sync(Row... rows) {
        when(apiClient.fetchAll()).thenReturn(List.of(rows));
        SyncResult result = syncService.syncAll();
        flushAndClear();
        return result;
    }

    private void flushAndClear() {
        // 메모리상의 엔티티만 확인하지 않고 SQL 반영 후 DB에서 다시 읽는다.
        entityManager.flush();
        entityManager.clear();
    }

    private ServiceEntity service(String externalId) {
        return services.findByServiceId(externalId).orElseThrow();
    }

    private Row row(String externalId) {
        return mapper.convertValue(fields(externalId), Row.class);
    }

    private Map<String, String> fields(String externalId) {
        Map<String, String> fields = new HashMap<>();
        fields.put("SVCID", externalId);
        fields.put("SVCNM", "테니스 예약");
        fields.put("MINCLASSNM", "테니스장");
        fields.put("PLACENM", "응봉공원");
        fields.put("AREANM", "성동구");
        fields.put("SVCSTATNM", "접수중");
        fields.put("PAYATNM", "무료");
        fields.put("SVCURL", "https://example.com/reservation");
        fields.put("RCPTENDDT", "2026-12-31 17:00:00.0");
        fields.put("X", "127.03");
        fields.put("Y", "37.55");
        fields.put("TELNO", "02-1234-5678");
        fields.put("V_MIN", "07:00");
        fields.put("V_MAX", "22:00");
        return fields;
    }
}
