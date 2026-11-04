package com.example.sporty.features.exerciseMatching.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.sporty.features.exerciseMatching.domain.entity.FacilityServiceSnapshotEntity;
import com.example.sporty.features.exerciseMatching.domain.entity.ProcessedEventEntity;
import com.example.sporty.features.exerciseMatching.event.FacilityServiceChangedEvent;
import com.example.sporty.features.exerciseMatching.repository.FacilityServiceSnapshotRepository;
import com.example.sporty.features.exerciseMatching.repository.ProcessedEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacilitySnapshotService {

    private static final String FACILITY_CHANGED_TOPIC = "facility.service.changed";

    private final FacilityServiceSnapshotRepository facilityServiceSnapshotRepository;
    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    public void apply(FacilityServiceChangedEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.info("[KAFKA] duplicate ignored. topic={}, eventId={}, serviceId={}",
                    FACILITY_CHANGED_TOPIC, event.eventId(), event.serviceId());
            return;
        }

        FacilityServiceSnapshotEntity snapshot = facilityServiceSnapshotRepository
                .findById(event.serviceId())
                .orElseGet(() -> newSnapshot(event));

        if (snapshot.getSourceUpdatedAt() != null
                && snapshot.getSourceUpdatedAt().isAfter(event.occurredAt())) {
            log.info("[KAFKA] stale facility snapshot ignored. eventId={}, serviceId={}, occurredAt={}",
                    event.eventId(), event.serviceId(), event.occurredAt());
            saveProcessedEvent(event.eventId());
            return;
        }

        snapshot.updateSnapshot(
                event.name(),
                event.locationName(),
                event.region(),
                event.isFree(),
                event.reservationDeadlineAt(),
                event.active(),
                event.occurredAt());

        facilityServiceSnapshotRepository.save(snapshot);
        saveProcessedEvent(event.eventId());
        log.info("[KAFKA] facility snapshot applied. eventId={}, serviceId={}, active={}",
                event.eventId(), event.serviceId(), event.active());
    }

    private void saveProcessedEvent(String eventId) {
        processedEventRepository.save(ProcessedEventEntity.builder()
                .eventId(eventId)
                .topic(FACILITY_CHANGED_TOPIC)
                .build());
    }

    private FacilityServiceSnapshotEntity newSnapshot(FacilityServiceChangedEvent event) {
        return FacilityServiceSnapshotEntity.builder()
                .serviceId(event.serviceId())
                .name(event.name())
                .locationName(event.locationName())
                .region(event.region())
                .isFree(event.isFree())
                .reservationDeadlineAt(event.reservationDeadlineAt())
                .active(event.active())
                .sourceUpdatedAt(event.occurredAt())
                .build();
    }
}
