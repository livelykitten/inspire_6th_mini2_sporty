package com.example.sporty.features.facilities.domain.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import com.example.sporty.features.facilities.domain.entity.ServiceEntity;

@Getter
@Builder
public class FacilityChangedEvent {

    private String eventId;
    private String occurredAt;
    private Long serviceId;
    private String name;
    private String locationName;
    private String region;
    private Boolean isFree;
    private String reservationDeadlineAt;
    private Boolean active;

    public static FacilityChangedEvent from(ServiceEntity service) {
        return FacilityChangedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .occurredAt(
                        LocalDateTime.now(ZoneId.of("Asia/Seoul"))
                                .truncatedTo(ChronoUnit.SECONDS)
                                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                )
                .serviceId(service.getId())
                .name(service.getName())
                .locationName(service.getLocation().getName())
                .region(service.getLocation().getRegion())
                .isFree(service.getIsFree())
                .reservationDeadlineAt(
                        service.getReservationDeadlineAt() == null
                                ? null
                                : service.getReservationDeadlineAt().format(
                                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                        )
                )
                .active(service.isActive())
                .build();

    }
}