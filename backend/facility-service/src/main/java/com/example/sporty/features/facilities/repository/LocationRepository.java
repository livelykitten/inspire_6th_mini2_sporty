package com.example.sporty.features.facilities.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.sporty.features.facilities.domain.entity.LocationEntity;

public interface LocationRepository extends JpaRepository<LocationEntity, Long> {

    Optional<LocationEntity> findByRegionAndNameAndFacilityName(
            String region,
            String name,
            String facilityName
    );
}
