package com.example.sporty.features.exerciseMatching.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import com.example.sporty.features.exerciseMatching.domain.entity.MatchParticipantEntity;

public interface MatchParticipantRepository extends JpaRepository<MatchParticipantEntity, Long> {

    // [EM-08] 내 매치 목록: 생성/참여 모두 조회하며 최근 시작 일정부터 표시한다.
    @EntityGraph(attributePaths = {"match", "match.service", "match.service.location"})
    List<MatchParticipantEntity> findAllByUser_IdOrderByMatch_StartAtDescMatch_IdDesc(Long userId);

    List<MatchParticipantEntity> findAllByMatch_IdOrderByIdAsc(Long matchId);

    // OWNER도 참가자로 등록되므로 역할 구분 없이 전체 참가 정보를 센다.
    long countByMatch_Id(Long matchId);

    boolean existsByMatch_IdAndUserId(Long matchId, Long userId);

    // 참가 정보를 조회한 뒤 getRole()로 OWNER/PARTICIPANT를 확인한다.
    Optional<MatchParticipantEntity> findByMatch_IdAndUserId(Long matchId, Long userId);

    void deleteAllByMatch_Id(Long matchId);
}
