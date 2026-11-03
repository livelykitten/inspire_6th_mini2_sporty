package com.example.sporty.features.facilities.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.sporty.features.facilities.domain.entity.FacilityOutbox;

public interface FacilityOutboxRepository
        extends JpaRepository<FacilityOutbox, Long> {
    List<FacilityOutbox> findByStatusOrderByIdAsc(String status);

    long countByStatus(String status);
}
