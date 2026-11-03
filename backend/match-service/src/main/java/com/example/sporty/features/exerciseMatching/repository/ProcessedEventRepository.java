package com.example.sporty.features.exerciseMatching.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.sporty.features.exerciseMatching.domain.entity.ProcessedEventEntity;

import jakarta.persistence.LockModeType;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, String> {
    @Modifying
    @Query(value = "INSERT INTO processed_event(event_id, topic, processed_at) "
            + "VALUES (:eventId, :topic, CURRENT_TIMESTAMP)", nativeQuery = true)
    void insert(@Param("eventId") String eventId, @Param ("topic") String topic);

    // Current/locking read after the guard, not a REPEATABLE READ snapshot re-read.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ProcessedEventEntity e where e.eventId = :eventId")
    Optional<ProcessedEventEntity> lock(@Param("eventId") String eventId);
}
