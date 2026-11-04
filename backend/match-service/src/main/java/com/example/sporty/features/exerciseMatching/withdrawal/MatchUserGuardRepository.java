package com.example.sporty.features.exerciseMatching.withdrawal;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface MatchUserGuardRepository extends JpaRepository<MatchUserGuard, Long> {
    // A duplicate INSERT must never reset a committed block. MariaDB atomic upsert.
    @Modifying
    @Query(value = "INSERT INTO match_user_guard(user_id, is_blocked) VALUES (:userId, 0) "
            + "ON DUPLICATE KEY UPDATE user_id = user_id", nativeQuery = true)
    void ensureExists(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from MatchUserGuard g where g.userId = :userId")
    Optional<MatchUserGuard> lock(@Param("userId") Long userId);
}
