package com.example.sporty.features.exerciseMatching.withdrawal;

import java.util.Optional;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface MatchOutboxRepository extends JpaRepository<MatchOutbox, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MatchOutbox> findFirstByStatusOrderByIdAsc(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from MatchOutbox o where o.messageKey = :key and o.topic in :topics order by o.id")
    List<MatchOutbox> findOutcomes(@Param("key") String key, @Param("topics") List<String> topics);
}
