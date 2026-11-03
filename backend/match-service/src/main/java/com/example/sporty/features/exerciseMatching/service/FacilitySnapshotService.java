package com.example.sporty.features.exerciseMatching.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.sporty.features.exerciseMatching.domain.entity.FacilityServiceSnapshotEntity;
import com.example.sporty.features.exerciseMatching.event.FacilityServiceChangedEvent;
import com.example.sporty.features.exerciseMatching.repository.FacilityServiceSnapshotRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacilitySnapshotService {

    private final FacilityServiceSnapshotRepository facilityServiceSnapshotRepository;

    @Transactional
    public void apply(FacilityServiceChangedEvent event) {
        FacilityServiceSnapshotEntity snapshot = facilityServiceSnapshotRepository
                .findById(event.serviceId())
                .orElseGet(() -> newSnapshot(event));

        if (snapshot.getSourceUpdatedAt() != null
                && snapshot.getSourceUpdatedAt().isAfter(event.occurredAt())) {
            log.info("[KAFKA] stale facility snapshot ignored. eventId={}, serviceId={}, occurredAt={}",
                    event.eventId(), event.serviceId(), event.occurredAt());
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
        log.info("[KAFKA] facility snapshot applied. eventId={}, serviceId={}, active={}",
                event.eventId(), event.serviceId(), event.active());
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
