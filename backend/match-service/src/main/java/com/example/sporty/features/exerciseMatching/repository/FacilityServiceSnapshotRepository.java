package com.example.sporty.features.exerciseMatching.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.sporty.features.exerciseMatching.domain.entity.FacilityServiceSnapshotEntity;

public interface FacilityServiceSnapshotRepository
        extends JpaRepository<FacilityServiceSnapshotEntity, Long> {
}
