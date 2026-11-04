package com.example.sporty.features.users.repository;

import com.example.sporty.features.users.domain.entity.UserEntity;
import com.example.sporty.features.users.domain.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    boolean existsByEmail(String email);

    Optional<UserEntity> findByEmail(String email);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UserEntity u
            set u.status = :pendingStatus,
                u.withdrawalSagaId = :sagaId,
                u.updatedAt = CURRENT_TIMESTAMP
            where u.id = :userId
              and u.status = :activeStatus
            """)
    int startWithdrawal(
            @Param("userId") Long userId,
            @Param("sagaId") String sagaId,
            @Param("activeStatus") UserStatus activeStatus,
            @Param("pendingStatus") UserStatus pendingStatus
    );

    @Query(
            value = "SELECT * FROM `user` WHERE id = :userId FOR UPDATE",
            nativeQuery = true
    )
    Optional<UserEntity> findCurrentByIdForUpdate(
            @Param("userId") Long userId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UserEntity u
            set u.status = :withdrawnStatus,
                u.withdrawalSagaId = null,
                u.withdrawnAt = CURRENT_TIMESTAMP,
                u.updatedAt = CURRENT_TIMESTAMP
            where u.id = :userId
              and u.status = :pendingStatus
              and u.withdrawalSagaId = :sagaId
            """)
    int completeWithdrawal(
            @Param("userId") Long userId,
            @Param("sagaId") String sagaId,
            @Param("pendingStatus") UserStatus pendingStatus,
            @Param("withdrawnStatus") UserStatus withdrawnStatus
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UserEntity u
            set u.status = :activeStatus,
                u.withdrawalSagaId = null,
                u.updatedAt = CURRENT_TIMESTAMP
            where u.id = :userId
              and u.status = :pendingStatus
              and u.withdrawalSagaId = :sagaId
            """)
    int compensateWithdrawal(
            @Param("userId") Long userId,
            @Param("sagaId") String sagaId,
            @Param("pendingStatus") UserStatus pendingStatus,
            @Param("activeStatus") UserStatus activeStatus
    );
}
