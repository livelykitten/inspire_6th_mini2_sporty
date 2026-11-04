package com.example.sporty.features.exerciseMatching.withdrawal;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

public interface MatchOutboxRepository extends JpaRepository<MatchOutbox, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MatchOutbox> findFirstByStatusOrderByIdAsc(String status);
}
