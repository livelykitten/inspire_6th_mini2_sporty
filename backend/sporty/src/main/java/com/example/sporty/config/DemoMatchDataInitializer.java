package com.example.sporty.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchEntity;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchParticipantEntity;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchParticipantRole;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;
import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;
import com.example.sporty.features.exerciseMatching.repository.MatchParticipantRepository;
import com.example.sporty.features.exerciseMatching.repository.MatchRepository;
import com.example.sporty.features.facilities.domain.entity.ServiceEntity;
import com.example.sporty.features.facilities.repository.ServiceRepository;
import com.example.sporty.features.users.domain.entity.UserEntity;
import com.example.sporty.features.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DemoMatchDataInitializer {

    private final ServiceRepository serviceRepository;
    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final UserRepository userRepository;

    @Transactional
    public void initialize() {

        // 재실행 시 데모 매치 중복 생성 방지
        if (matchRepository.count() > 0) {
            log.info("기존 매치가 존재하여 데모 매치 생성을 건너뜁니다.");
            return;
        }

        /*
         * PK 1을 직접 사용하지 않고
         * data.sql에 존재하는 데모 계정을 email로 찾는다.
         */
        UserEntity owner = userRepository.findByEmail("a@b.c")
                .orElseThrow(() ->
                        new IllegalStateException(
                                "데모 OWNER 사용자(a@b.c)가 없습니다."
                        )
                );

        UserEntity participant = userRepository.findByEmail("b@c.d")
                .orElseThrow(() ->
                        new IllegalStateException(
                                "데모 PARTICIPANT 사용자(b@c.d)가 없습니다."
                        )
                );

        /*
         * 서울시 API 동기화로 생성된 실제 시설을 사용한다.
         */
        ServiceEntity futsalService =
                findFacilityService("풋살장");

        ServiceEntity basketballService =
                findFacilityService("농구장");

        ServiceEntity tennisService =
                findFacilityService("테니스장");

        LocalDate tomorrow = LocalDate.now().plusDays(1);

        MatchEntity match1 = createMatch(
                "퇴근 후 풋살 같이 해요!",
                "초보자도 편하게 참여할 수 있습니다.",
                tomorrow.atTime(18, 0),
                tomorrow.atTime(20, 0),
                6,
                SkillLevel.BEGINNER,
                SportType.FUTSAL,
                GenderGroup.MIXED,
                futsalService
        );

        MatchEntity match2 = createMatch(
                "저녁 풋살 멤버 모집",
                "중급자 위주로 재미있게 경기합니다.",
                tomorrow.plusDays(1).atTime(19, 0),
                tomorrow.plusDays(1).atTime(21, 0),
                10,
                SkillLevel.INTERMEDIATE,
                SportType.FUTSAL,
                GenderGroup.MALE,
                futsalService
        );

        MatchEntity match3 = createMatch(
                "주말 농구 같이 하실 분",
                "가볍게 농구하실 분 모집합니다.",
                tomorrow.plusDays(2).atTime(14, 0),
                tomorrow.plusDays(2).atTime(16, 0),
                8,
                SkillLevel.BEGINNER,
                SportType.BASKETBALL,
                GenderGroup.MIXED,
                basketballService
        );

        MatchEntity match4 = createMatch(
                "농구 중급자 게임",
                "중급자 이상 인원 모집합니다.",
                tomorrow.plusDays(3).atTime(18, 0),
                tomorrow.plusDays(3).atTime(20, 0),
                10,
                SkillLevel.INTERMEDIATE,
                SportType.BASKETBALL,
                GenderGroup.MIXED,
                basketballService
        );

        MatchEntity match5 = createMatch(
                "테니스 초보자 모집",
                "테니스를 처음 시작하신 분도 환영합니다.",
                tomorrow.plusDays(4).atTime(10, 0),
                tomorrow.plusDays(4).atTime(12, 0),
                4,
                SkillLevel.BEGINNER,
                SportType.TENNIS,
                GenderGroup.MIXED,
                tennisService
        );

        MatchEntity match6 = createMatch(
                "테니스 랠리 연습",
                "중급자 랠리 연습 위주입니다.",
                tomorrow.plusDays(5).atTime(16, 0),
                tomorrow.plusDays(5).atTime(18, 0),
                4,
                SkillLevel.INTERMEDIATE,
                SportType.TENNIS,
                GenderGroup.MIXED,
                tennisService
        );

        List<MatchEntity> matches = matchRepository.saveAll(
                List.of(
                        match1,
                        match2,
                        match3,
                        match4,
                        match5,
                        match6
                )
        );

        /*
         * 모든 매치의 생성자(owner)를 참가자로 등록.
         *
         * 우리 프로젝트 정책:
         * 매치 생성자도 match_participant에 OWNER로 존재한다.
         */
        List<MatchParticipantEntity> owners = matches.stream()
                .map(match ->
                        MatchParticipantEntity.builder()
                                .user(owner)
                                .match(match)
                                .role(MatchParticipantRole.OWNER)
                                .build()
                )
                .toList();

        matchParticipantRepository.saveAll(owners);

        /*
         * 첫 번째 매치에는 user2도 참가시켜서
         * 참가자 목록/현재 인원 표시 등을 데모할 수 있게 한다.
         */
        MatchParticipantEntity demoParticipant =
                MatchParticipantEntity.builder()
                        .user(participant)
                        .match(match1)
                        .role(MatchParticipantRole.PARTICIPANT)
                        .build();

        matchParticipantRepository.save(demoParticipant);

        log.info(
                "데모 매치 {}건 생성 완료 - 풋살/농구/테니스 실제 서울시 시설 사용",
                matches.size()
        );
    }

    private ServiceEntity findFacilityService(String facilityName) {

        return serviceRepository
                .findFirstByActiveTrueAndLocation_FacilityNameOrderByIdAsc(
                        facilityName
                )
                .orElseThrow(() ->
                        new IllegalStateException(
                                "서울시 시설 데이터에서 "
                                        + facilityName
                                        + " 서비스를 찾지 못했습니다."
                        )
                );
    }

    private MatchEntity createMatch(
            String title,
            String description,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Integer maxParticipant,
            SkillLevel skillLevel,
            SportType sportType,
            GenderGroup genderGroup,
            ServiceEntity service
    ) {
        return MatchEntity.builder()
                .title(title)
                .description(description)
                .startAt(startAt)
                .endAt(endAt)
                .maxParticipant(maxParticipant)
                .status(MatchStatus.RECRUITING)
                .skillLevel(skillLevel)
                .sportType(sportType)
                .genderGroup(genderGroup)
                .service(service)
                .build();
    }
}