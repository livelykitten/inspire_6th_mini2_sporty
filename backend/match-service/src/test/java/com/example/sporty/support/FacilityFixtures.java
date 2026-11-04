package com.example.sporty.support;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import com.example.sporty.features.exerciseMatching.domain.entity.FacilityServiceSnapshotEntity;

import jakarta.persistence.EntityManager;

/** Test fixtures owned by match-service's facility snapshot boundary. */
public final class FacilityFixtures {
    private static final AtomicLong IDS = new AtomicLong(10_000L);

    private FacilityFixtures() {}

    public static long newServiceId() {
        return IDS.incrementAndGet();
    }

    public static FacilityServiceSnapshotEntity create(EntityManager em, long serviceId) {
        return create(em, serviceId, true);
    }

    public static FacilityServiceSnapshotEntity create(EntityManager em, long serviceId, boolean isFree) {
        FacilityServiceSnapshotEntity service = service(serviceId, isFree);
        em.persist(service);
        return service;
    }

    public static FacilityServiceSnapshotEntity service(long serviceId, boolean isFree) {
        return FacilityServiceSnapshotEntity.builder()
                .serviceId(serviceId)
                .name("Test service " + serviceId)
                .locationName("Test location")
                .region("강남구")
                .isFree(isFree)
                .active(true)
                .sourceUpdatedAt(LocalDateTime.now())
                .build();
    }
}
