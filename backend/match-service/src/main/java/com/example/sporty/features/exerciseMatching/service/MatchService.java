package com.example.sporty.features.exerciseMatching.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.example.sporty.features.commons.exception.users.UserNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.sporty.features.commons.exception.exerciseMatching.MatchDeleteForbiddenException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchModifyForbiddenException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchNotFoundException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchOwnerCannotLeaveException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchParticipantNotFoundException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchAlreadyJoinedException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchAlreadyStartedException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchFullException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchRecruitmentClosedException;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchDetailResponseDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchModifyRequestDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchParticipantResponseDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchParticipantSummaryDto;
import com.example.sporty.features.commons.exception.matches.MatchUserNotFoundException;
import com.example.sporty.features.commons.exception.matches.WithdrawnUserFoundException;
import com.example.sporty.features.commons.exception.matches.ServiceNotFoundException;
import com.example.sporty.features.facilities.domain.entity.ServiceEntity;
import com.example.sporty.features.facilities.repository.ServiceRepository;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchCreateRequestDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchResponseDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchSearchRequestDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MyMatchResponseDto;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchEntity;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchParticipantEntity;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchParticipantRole;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;
import com.example.sporty.features.exerciseMatching.repository.MatchParticipantRepository;
import com.example.sporty.features.exerciseMatching.repository.MatchRepository;
import com.example.sporty.features.users.domain.entity.UserEntity;
import com.example.sporty.features.users.domain.entity.UserStatus;
import com.example.sporty.features.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.example.sporty.features.profiles.domain.entity.ProfileEntity;
import com.example.sporty.features.profiles.repository.ProfileRepository;

@Service 
@RequiredArgsConstructor 
@Transactional(readOnly = true)
public class MatchService {
    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final ServiceRepository serviceRepository;

    // [EM-08] 내 매치 목록 조회
    public List<MyMatchResponseDto> getMyMatches(Long userId) {
        // 1. 로그인 회원 확인
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new WithdrawnUserFoundException();
        }

        // 2. 본인이 생성하거나 참여한 매치 조회
        List<MatchParticipantEntity> participants = matchParticipantRepository
                .findAllByUser_IdOrderByMatch_StartAtDescMatch_IdDesc(userId);
        if (participants.isEmpty()) {
            return List.of();
        }

        // 3. 기존 전체 목록 조회와 동일하게 참가 인원을 한 번에 집계
        List<Long> matchIds = participants.stream().map(participant -> participant.getMatch().getId()).toList();
        Map<Long, Long> countsByMatchId = matchRepository.countByMatchIds(matchIds).stream()
                .collect(Collectors.toMap(row -> row.getMatchId(), row -> row.getParticipantCount()));

        // 4. 생성자/일반 참가자 역할을 포함한 목록 반환
        return participants.stream()
                .map(participant -> MyMatchResponseDto.toResponseDto(participant,
                        countsByMatchId.getOrDefault(participant.getMatch().getId(), 0L)))
                .toList();
    }

    /**
     * EM-07: 인증된 일반 참가자의 참가 정보만 삭제한다. 경기 시작 후에도 탈퇴 가능하다.
     * OWNER는 탈퇴 대신 매치 삭제 기능을 사용해야 한다.
     * 시작 전에는 빈자리가 생기면 모집을 재개하고, 시작 이후에는 모집 상태를 유지한다.
     */
    @Transactional
    public void leaveMatch(Long matchId, Long userId) {
        MatchEntity match = matchRepository
                .findByIdForUpdate(matchId)
                .orElseThrow(MatchNotFoundException::new);

        MatchParticipantEntity participant = matchParticipantRepository
                .findByMatch_IdAndUserId(matchId, userId)
                .orElseThrow(MatchParticipantNotFoundException::new);

        // 매치 생성자는 탈퇴할 수 없다.
        if (participant.getRole() == MatchParticipantRole.OWNER) {
            throw new MatchOwnerCannotLeaveException();
        }

        matchParticipantRepository.delete(participant);

        // 같은 트랜잭션의 삭제를 반영한 인원으로 확인하며, 참여 API와 매치 잠금을 공유한다.
        // CLOSED 상태에서 탈퇴했을 때, 매칭 시작 전 + 인원 제한 수 보다 적으면 다시 RECRUITING 상태로 변경
        if (match.getStatus() == MatchStatus.CLOSED
                && match.getStartAt().isAfter(LocalDateTime.now())
                && matchParticipantRepository.countByMatch_Id(matchId) < match.getMaxParticipant()) {
            match.reopenRecruitment();
        }
    }

    /** [USR-04] 회원탈퇴: 생성한 매치는 전체 삭제, 다른 매치에서는 본인 참가 기록만 삭제한다. */
    @Transactional
    public void removeMatchesForWithdrawal(Long userId) {
        List<Long> matchIds = matchParticipantRepository
                .findAllByUser_IdOrderByMatch_StartAtDescMatch_IdDesc(userId).stream()
                .map(participant -> participant.getMatch().getId())
                .distinct().sorted().toList();

        // 참가/탈퇴 API와 같은 매치 잠금을 사용하고, 여러 매치는 ID 순으로 잠근다.
        for (Long matchId : matchIds) {
            if (matchRepository.findByIdForUpdate(matchId).isEmpty()) continue;
            var participation = matchParticipantRepository.findByMatch_IdAndUserId(matchId, userId);
            if (participation.isEmpty()) continue;
            if (participation.get().getRole() == MatchParticipantRole.OWNER) {
                deleteMatch(matchId, userId);
            } else {
                leaveMatch(matchId, userId);
            }
        }
    }

    // EM-06: 참가자 저장과 정원 도달 시 모집 마감을 하나의 트랜잭션으로 처리한다.
    @Transactional
    public MatchParticipantResponseDto joinMatch(Long matchId, Long userId) {
        MatchEntity match = matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(MatchNotFoundException::new);
        
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(MatchUserNotFoundException::new);

        // OWNER도 이미 등록된 참가자이므로 역할과 관계없이 중복 참여를 막는다.
        if (matchParticipantRepository.existsByMatch_IdAndUserId(matchId, userId)) {
            throw new MatchAlreadyJoinedException();
        }

        if (match.getStatus() != MatchStatus.RECRUITING) {
            throw new MatchRecruitmentClosedException();
        }

        // 잠금을 기다리는 동안 시작 시각이 지났을 수도 있으므로 조회 후 현재 시각을 확인한다.
        if (!match.getStartAt().isAfter(LocalDateTime.now())) {
            throw new MatchAlreadyStartedException();
        }

        long participantCount = matchParticipantRepository.countByMatch_Id(matchId);

        if (participantCount >= match.getMaxParticipant()) {
            throw new MatchFullException();
        }

        MatchParticipantEntity participant = matchParticipantRepository.save(
                MatchParticipantEntity.builder()
                        .match(match)
                        .user(user)
                        .role(MatchParticipantRole.PARTICIPANT)
                        .build());

        if (participantCount + 1 == match.getMaxParticipant()) {
            match.closeRecruitment();
        }

        return MatchParticipantResponseDto.builder()
                .matchParticipantId(participant.getId())
                .matchId(matchId)
                .userId(participant.getUser().getId())
                .role(participant.getRole())
                .build();
    }


    // EM-05: 참가 기록과 매치를 한 트랜잭션에서 삭제한다.
    @Transactional
    public void deleteMatch(Long matchId, Long userId) {
        MatchEntity match = matchRepository.findById(matchId)
                .orElseThrow(MatchNotFoundException::new);

        matchParticipantRepository.findByMatch_IdAndUserId(matchId, userId)
                .filter(participant -> participant.getRole() == MatchParticipantRole.OWNER)
                .orElseThrow(MatchDeleteForbiddenException::new);

        // 외래 키로 연결된 참가 기록을 먼저 삭제한 뒤 매치를 삭제한다.
        matchParticipantRepository.deleteAllByMatch_Id(matchId);
        matchRepository.delete(match);
    }

    @Transactional 
    public Long createMatch(MatchCreateRequestDto req) {

        System.out.println("debug >> MatchService.createMatch(), req: " + req);

        // 1. get user id from the authentication context
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = Long.valueOf(auth.getName());


        UserEntity userEntity = 
            userRepository.findById(userId)
            .orElseThrow(() -> new MatchUserNotFoundException());

        if (userEntity.getStatus() != UserStatus.ACTIVE) {
            throw new WithdrawnUserFoundException();
        }

        // Validate the catalog before creating either match or participant records.
        ServiceEntity service = serviceRepository.findById(req.getServiceId())
            .orElseThrow(ServiceNotFoundException::new);

        // 3. create MatchEntity and save
        MatchEntity match = req.toEntity(service);
        MatchEntity savedMatchEntity =
            matchRepository.save(match);
        

        // 4. create MatchParticipant entity and save  

        MatchParticipantEntity matchParticipantEntity
            = MatchParticipantEntity.builder()
            .user(userEntity) 
            .match(savedMatchEntity)
            .role(MatchParticipantRole.OWNER)
            .build();
        
        matchParticipantRepository.save(matchParticipantEntity);

        
        // 5. return matchId
        return savedMatchEntity.getId();
    }

    @Transactional
    public MatchDetailResponseDto modifyMatch(
        Long matchId,
        Long userId,
        MatchModifyRequestDto req
    ) {
        // 매치 조회
        MatchEntity match = matchRepository.findById(matchId)
                .orElseThrow(MatchNotFoundException::new);

        // 생성자 권한 확인
        matchParticipantRepository.findByMatch_IdAndUserId(matchId, userId)
                .filter(participant -> participant.getRole() == MatchParticipantRole.OWNER)
                .orElseThrow(MatchModifyForbiddenException::new);
        
        // 업데이트

        match.update(
            req.getTitle(),
            req.getDescription(),
            req.getStartAt(),
            req.getEndAt(),
            req.getMaxParticipant(),
            req.getSkillLevel(),
            req.getGenderGroup()
        );

        return getMatchDetail(matchId, userId);
    }

    public List<MatchResponseDto> searchMatches(MatchSearchRequestDto req) {
        System.out.println("debug >> MatchService.searchMatches(), req: " + req);

        List<MatchEntity> matches = matchRepository.searchMatches(
            req.getServiceId(),
            escapeSearchKeyword(req.getTitleKeyword()),
            escapeSearchKeyword(req.getDescriptionKeyword()),
            req.getStartAt(),
            req.getEndAt(),
            req.getMaxParticipant(),
            req.getSkillLevel(),
            req.getSportType(),
            req.getGenderGroup(),
            req.getStatus(),
            escapeSearchKeyword(req.getRegion()),
            req.getIsFree()
        );

        if (matches.isEmpty()) {
            return List.of();
        }

        List<Long> matchIds = matches.stream().map(match -> match.getId()).toList();

        Map<Long, Long> countsByMatchId = 
            matchRepository.countByMatchIds(matchIds).stream()
            .collect(Collectors.toMap(r -> r.getMatchId(), r -> r.getParticipantCount()));


        return matches.stream()
            .map(match -> MatchResponseDto.toResponseDto(
                match,
                countsByMatchId.getOrDefault(match.getId(), 0L)
            )).toList();

    }

    private String escapeSearchKeyword(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return null;
        }

        // Must match the repository's LIKE ESCAPE character; escape it first.
        return keyword.replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_");
    }
    /**
     * EM-03: 기본 정보, 참가자 프로필과 조회 사용자의 참여 상태를 반환한다.
     * OWNER도 참가 인원에 포함하며, 비로그인 요청의 userId는 null이다.
     */
    public MatchDetailResponseDto getMatchDetail(Long matchId, Long userId) {
        MatchEntity match = matchRepository.findById(matchId)
                .orElseThrow(MatchNotFoundException::new);

        List<MatchParticipantEntity> participants =
                matchParticipantRepository.findAllByMatch_IdOrderByIdAsc(matchId);
        
        
        List<Long> participantUserIds = participants.stream()
                .map(MatchParticipantEntity::getUser)
                .filter(Objects::nonNull)
                .map(UserEntity::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        
        
        Map<Long, ProfileEntity> profilesByUserId = participantUserIds.isEmpty()
                ? Map.of()
                : profileRepository.findAllByUser_IdIn(participantUserIds).stream()
                        .collect(Collectors.toMap(profile -> profile.getUser().getId(), Function.identity()));

        List<MatchParticipantSummaryDto> participantSummaries = new ArrayList<>();
        boolean isOwner = false;
        boolean isParticipant = false;


        for (MatchParticipantEntity participant : participants) {
            ProfileEntity profile = participant.getUser().getId() == null
                    ? null : profilesByUserId.get(participant.getUser().getId());

            // 프로필이 누락되어도 참가 정보와 인원은 유지한다.
            participantSummaries.add(MatchParticipantSummaryDto.builder()
                    .profileId(profile == null ? null : profile.getId())
                    .nickname(profile == null ? null : profile.getNickname())
                    .imageUrl(profile == null ? null : profile.getImageUrl())
                    .role(participant.getRole())
                    .build());

            if (userId != null && userId.equals(participant.getUser().getId())) {
                isParticipant = true;
                if (participant.getRole() == MatchParticipantRole.OWNER) {
                    isOwner = true;
                }
            }
        }

        return MatchDetailResponseDto.builder()
                .matchId(match.getId())
                .title(match.getTitle())
                .description(match.getDescription())
                .startAt(match.getStartAt())
                .endAt(match.getEndAt())
                .maxParticipant(match.getMaxParticipant())
                .currentParticipantCount(participants.size())
                .status(match.getStatus())
                .skillLevel(match.getSkillLevel())
                .sportType(match.getSportType())
                .genderGroup(match.getGenderGroup())
                .serviceId(match.getService().getId())
                .participants(participantSummaries)
                .isOwner(isOwner)
                .isParticipant(isParticipant)
                .region(match.getService().getLocation().getRegion())
                .serviceName(match.getService().getName())
                .locationName(match.getService().getLocation().getName())
                .build();
    }
}
