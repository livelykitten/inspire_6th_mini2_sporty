package com.example.sporty.features.exerciseMatching.withdrawal;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
    @Modifying
    @Query(value = "INSERT INTO processed_event(event_id, topic, processed_at) "
            + "VALUES (:eventId, :topic, CURRENT_TIMESTAMP)", nativeQuery = true)
    void insert(@Param("eventId") String eventId, @Param("topic") String topic);

    // Current/locking read after the guard, not a REPEATABLE READ snapshot re-read.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ProcessedEvent e where e.eventId = :eventId")
    Optional<ProcessedEvent> lock(@Param("eventId") String eventId);
}
