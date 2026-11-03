package com.example.sporty.features.exerciseMatching.event;

import java.time.LocalDateTime;

public record FacilityServiceChangedEvent(
        String eventId,
        LocalDateTime occurredAt,
        Long serviceId,
        String name,
        String locationName,
        String region,
        Boolean isFree,
        LocalDateTime reservationDeadlineAt,
        Boolean active
) {
}
