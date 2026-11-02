package com.example.sporty.features.facilities.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.sporty.features.facilities.domain.entity.LocationEntity;
import com.example.sporty.features.facilities.domain.entity.ServiceEntity;
import com.example.sporty.features.facilities.repository.LocationRepository;
import com.example.sporty.features.facilities.repository.ServiceRepository;
import com.example.sporty.features.openapi.SeoulFacilityApiClient;
import com.example.sporty.features.openapi.domain.dto.SeoulFacilityResponseDto.Row;

import lombok.RequiredArgsConstructor;

/**
 * 서울시 체육시설 전체 목록을 로컬 DB와 동기화한다.
 */
@Service
@RequiredArgsConstructor
public class FacilitySyncService {

    private static final Logger log =
            LoggerFactory.getLogger(FacilitySyncService.class);
    private static final DateTimeFormatter SEOUL_DATE_TIME_FORMATTER =
            new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd HH:mm:ss")
                    .optionalStart()
                    .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
                    .optionalEnd()
                    .toFormatter();

    private final SeoulFacilityApiClient apiClient;
    private final LocationRepository locationRepository;
    private final ServiceRepository serviceRepository;
    private final TransactionTemplate transactionTemplate;

    public SyncResult syncAll() {
        List<Row> rows = apiClient.fetchAll();
        if (rows.isEmpty()) {
            throw new IllegalStateException(
                    "서울시 체육시설 데이터가 비어 있어 동기화를 중단합니다."
            );
        }

        SyncResult result = transactionTemplate.execute(
                status -> synchronize(rows)
        );

        if (result == null) {
            throw new IllegalStateException("체육시설 DB 동기화에 실패했습니다.");
        }

        log.info(
                "체육시설 동기화 완료: 조회 {}, 신규 {}, 갱신 {}, 비활성화 {}, 제외 {}",
                result.fetchedCount(),
                result.createdCount(),
                result.updatedCount(),
                result.deactivatedCount(),
                result.skippedCount()
        );
        return result;
    }

    private SyncResult synchronize(List<Row> rows) {
        Map<LocationKey, LocationEntity> locationCache = loadLocationCache();
        Map<String, ServiceEntity> serviceCache = loadServiceCache();
        Set<String> seenServiceIds = new HashSet<>();

        int createdCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;

        for (Row row : rows) {
            String serviceId = trimToNull(row.getSvcId());
            String serviceName = trimToNull(row.getSvcNm());
            String region = trimToNull(row.getAreaNm());
            String placeName = trimToNull(row.getPlaceNm());
            String facilityName = trimToNull(row.getMinClassNm());

            if (serviceId == null
                    || serviceName == null
                    || region == null
                    || placeName == null
                    || facilityName == null) {
                skippedCount++;
                log.warn("필수값이 없는 서울시 체육시설 데이터를 제외합니다.");
                continue;
            }

            LocationEntity location = findOrCreateLocation(
                    row,
                    region,
                    placeName,
                    facilityName,
                    locationCache
            );

            ServiceEntity service = serviceCache.get(serviceId);
            if (service == null) {
                service = createService(
                        row,
                        location,
                        serviceId,
                        serviceName
                );
                serviceRepository.save(service);
                serviceCache.put(serviceId, service);
                createdCount++;
            } else {
                service.update(
                        location,
                        serviceName,
                        trimToNull(row.getSvcStatNm()),
                        parseTime(row.getVMin(), false),
                        parseTime(row.getVMax(), true),
                        parseIsFree(row.getPayAtNm()),
                        trimToNull(row.getSvcUrl()),
                        parseDateTime(row.getRcptEndDt())
                );
                updatedCount++;
            }

            seenServiceIds.add(serviceId);
        }

        int deactivatedCount = 0;
        for (ServiceEntity service : serviceCache.values()) {
            if (service.isActive()
                    && !seenServiceIds.contains(service.getServiceId())) {
                service.deactivate();
                deactivatedCount++;
            }
        }

        return new SyncResult(
                rows.size(),
                createdCount,
                updatedCount,
                deactivatedCount,
                skippedCount
        );
    }

    private LocationEntity findOrCreateLocation(
            Row row,
            String region,
            String placeName,
            String facilityName,
            Map<LocationKey, LocationEntity> locationCache
    ) {
        LocationKey key = new LocationKey(region, placeName, facilityName);
        String contact = trimToNull(row.getTelNo());
        BigDecimal latitude = parseDecimal(row.getY());
        BigDecimal longitude = parseDecimal(row.getX());

        LocationEntity location = locationCache.get(key);
        if (location == null) {
            LocationKey legacyKey = new LocationKey(region, placeName, null);
            location = locationCache.remove(legacyKey);
        }

        if (location == null) {
            location = LocationEntity.builder()
                    .region(region)
                    .name(placeName)
                    .facilityName(facilityName)
                    .contact(contact)
                    .latitude(latitude)
                    .longitude(longitude)
                    .build();
            locationRepository.save(location);
        } else {
            location.update(
                    facilityName,
                    contact,
                    latitude,
                    longitude
            );
        }
        locationCache.put(key, location);

        return location;
    }

    private ServiceEntity createService(
            Row row,
            LocationEntity location,
            String serviceId,
            String serviceName
    ) {
        return ServiceEntity.builder()
                .location(location)
                .serviceId(serviceId)
                .name(serviceName)
                .status(trimToNull(row.getSvcStatNm()))
                .startTime(parseTime(row.getVMin(), false))
                .endTime(parseTime(row.getVMax(), true))
                .isFree(parseIsFree(row.getPayAtNm()))
                .url(trimToNull(row.getSvcUrl()))
                .reservationDeadlineAt(parseDateTime(row.getRcptEndDt()))
                .build();
    }

    private Map<LocationKey, LocationEntity> loadLocationCache() {
        Map<LocationKey, LocationEntity> cache = new HashMap<>();
        for (LocationEntity location : locationRepository.findAll()) {
            cache.putIfAbsent(
                    new LocationKey(
                            location.getRegion(),
                            location.getName(),
                            location.getFacilityName()
                    ),
                    location
            );
        }
        return cache;
    }

    private Map<String, ServiceEntity> loadServiceCache() {
        Map<String, ServiceEntity> cache = new HashMap<>();
        for (ServiceEntity service : serviceRepository.findAll()) {
            cache.put(service.getServiceId(), service);
        }
        return cache;
    }

    private BigDecimal parseDecimal(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }

        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            log.warn("좌표 변환에 실패했습니다: {}", normalized);
            return null;
        }
    }

    private LocalTime parseTime(String value, boolean endTime) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
    
        if ("24:00".equals(normalized)) {
            return endTime
                    ? LocalTime.of(23, 59, 59)
                    : LocalTime.MIDNIGHT;
        }
    
        try {
            return LocalTime.parse(normalized);
        } catch (DateTimeParseException exception) {
            log.warn("운영시간 변환에 실패했습니다: {}", normalized);
            return null;
        }
    }

    private LocalDateTime parseDateTime(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }

        try {
            return LocalDateTime.parse(
                    normalized,
                    SEOUL_DATE_TIME_FORMATTER
            );
        } catch (DateTimeParseException exception) {
            log.warn("예약 접수 마감 일시 변환에 실패했습니다: {}", normalized);
            return null;
        }
    }

    private Boolean parseIsFree(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }

        if (normalized.startsWith("무료")) {
            return true;
        }
        if (normalized.startsWith("유료")) {
            return false;
        }

        log.warn("유무료 여부를 해석할 수 없습니다: {}", normalized);
        return null;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record LocationKey(
            String region,
            String name,
            String facilityName
    ) {
    }

    public record SyncResult(
            int fetchedCount,
            int createdCount,
            int updatedCount,
            int deactivatedCount,
            int skippedCount
    ) {
    }
}
