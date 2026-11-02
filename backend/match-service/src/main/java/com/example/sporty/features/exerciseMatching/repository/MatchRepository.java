package com.example.sporty.features.exerciseMatching.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchEntity;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;
import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;

import jakarta.persistence.LockModeType;

public interface MatchRepository extends JpaRepository<MatchEntity, Long> {

    
    // 같은 매치의 참여 요청은 앞선 트랜잭션이 끝난 뒤 인원과 중복 여부를 확인한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MatchEntity m where m.id = :matchId")
    Optional<MatchEntity> findByIdForUpdate(@Param("matchId") Long matchId);

    @Query ("""
            SELECT m
            FROM MatchEntity m
            JOIN FETCH m.service s
            JOIN FETCH s.location l
            WHERE (:serviceId IS NULL OR m.service.id = :serviceId)
            AND (:titleKeyword IS NULL OR LOWER(m.title) LIKE LOWER(CONCAT('%', :titleKeyword, '%')) ESCAPE '!')
            AND (:descriptionKeyword IS NULL OR LOWER(m.description) LIKE LOWER(CONCAT('%', :descriptionKeyword, '%')) ESCAPE '!')
            AND (:startAt IS NULL OR m.startAt >= :startAt)
            AND (:endAt IS NULL OR m.endAt <= :endAt)
            AND (:maxParticipant IS NULL OR m.maxParticipant <= :maxParticipant)
            AND (:skillLevel IS NULL OR m.skillLevel = :skillLevel)
            AND (:sportType IS NULL OR m.sportType = :sportType)
            AND (:genderGroup IS NULL OR m.genderGroup = :genderGroup)
            AND (:status IS NULL OR m.status = :status)
            AND (:region IS NULL OR LOWER(l.region) LIKE LOWER(CONCAT('%', :region, '%')) ESCAPE '!')
            AND (:isFree IS NULL OR s.isFree = :isFree)
        """)
    List<MatchEntity> searchMatches(
        @Param ("serviceId") Long serviceId,
        @Param("titleKeyword") String titleKeyword,
        @Param("descriptionKeyword") String descriptionKeyword,
        @Param("startAt") LocalDateTime startAt,
        @Param("endAt") LocalDateTime endAt,
        @Param("maxParticipant") Integer maxParticipant,
        @Param("skillLevel") SkillLevel skillLevel,
        @Param("sportType") SportType sportType,
        @Param("genderGroup") GenderGroup genderGroup,
        @Param("status") MatchStatus status, 
        @Param("region") String region,
        @Param("isFree") Boolean isFree
    );

    interface MatchParticipantCount {
        Long getMatchId();
        Long getParticipantCount();
    }

    @Query("""
        SELECT
            mp.match.id as matchId, 
            COUNT(mp) as participantCount
        FROM MatchParticipantEntity mp
        WHERE mp.match.id IN :matchIds
        GROUP BY mp.match.id
    """)
    List<MatchParticipantCount> countByMatchIds(@Param("matchIds") List<Long> matchIds);
}
