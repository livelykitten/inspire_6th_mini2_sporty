package com.example.sporty.features.users.repository;

import com.example.sporty.features.users.domain.entity.UserOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UserOutboxRepository
        extends JpaRepository<UserOutbox, Long> {

    List<UserOutbox> findTop100ByStatusOrderByIdAsc(String status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UserOutbox o
            set o.status = 'SENT',
                o.sentAt = :sentAt
            where o.id = :id
              and o.status = 'PENDING'
            """)
    int markSent(
            @Param("id") Long id,
            @Param("sentAt") LocalDateTime sentAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UserOutbox o
            set o.attemptCount = o.attemptCount + 1
            where o.id = :id
              and o.status = 'PENDING'
            """)
    int recordFailure(@Param("id") Long id);
}