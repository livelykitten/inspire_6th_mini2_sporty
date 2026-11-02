package com.example.sporty.features.ai.agent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.example.sporty.features.ai.domain.dto.AiRecommendDto;
import com.example.sporty.features.commons.exception.ai.AiRecommendException;
import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchResponseDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchSearchRequestDto;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;
import com.example.sporty.features.exerciseMatching.repository.MatchParticipantRepository;
import com.example.sporty.features.exerciseMatching.service.MatchService;
import com.example.sporty.features.users.domain.dto.UserInfoResponseDto;
import com.example.sporty.features.users.domain.entity.Gender;
import com.example.sporty.features.users.service.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class MatchRecommendAiAgent {

    private static final int RECOMMEND_SIZE = 3;
    private static final int CANDIDATE_MAX = 20;
    private static final String FAIL_MESSAGE = "추천 매치를 만들지 못했습니다. 잠시 후 다시 시도해주세요.";

    private final ChatClient matchRecommendChatClient;
    private final UserService userService;
    private final MatchService matchService;
    private final MatchParticipantRepository matchParticipantRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    // ChatClient 빈이 여러 개라 이름으로 지정한다 (AiAgentConfig의 @Bean 메서드명)
    public MatchRecommendAiAgent(@Qualifier("matchRecommendChatClient") ChatClient matchRecommendChatClient,
            UserService userService, MatchService matchService,
            MatchParticipantRepository matchParticipantRepository) {
        this.matchRecommendChatClient = matchRecommendChatClient;
        this.userService = userService;
        this.matchService = matchService;
        this.matchParticipantRepository = matchParticipantRepository;
    }

    public List<MatchResponseDto> recommend(Long userId) {
        // 1. 회원 정보: 성별, 활동 자치구, 선호 종목
        UserInfoResponseDto me = userService.getMyInfo(userId);

        // 2. 후보: 모집중 + 아직 시작 전 → 조건 거르기 → 내가 만들었거나 참가한 매치 제외 → 최대 20개
        List<MatchResponseDto> recruiting = matchService.searchMatches(MatchSearchRequestDto.builder()
                .status(MatchStatus.RECRUITING)
                .startAt(LocalDateTime.now())
                .build());

        List<MatchResponseDto> candidates = candidates(me, recruiting).stream()
                .filter(match -> !matchParticipantRepository.existsByMatch_IdAndUserId(match.getMatchId(), userId))
                .limit(CANDIDATE_MAX)
                .toList();

        System.out.println("debug >>>> match recommend ai agent candidates : " + candidates.size());

        // 3. 후보가 없으면 AI를 호출하지 않는다.
        if (candidates.isEmpty()) {
            return List.of();
        }

        // 4. AI가 추천 순서대로 ID를 고른다.
        String systemPrompt = """
                당신은 운동 매칭 서비스의 추천 에이전트입니다.

                ## 규칙
                1. 회원 정보와 후보 매치 목록을 보고 회원에게 잘 맞는 순서대로 매치 %d개를 골라 recommendMatches 도구를 호출한다.
                   후보가 %d개보다 적으면 후보 전부를 순서대로 전달한다.
                2. 선호 종목과 활동 자치구가 모두 맞는 매치를 가장 우선하고, 그다음 선호 종목, 그다음 같은 자치구 순으로 고려한다.
                3. 조건이 비슷하면 모집 인원에 여유가 있고 일정이 가까운 매치를 우선한다.
                4. 후보 목록에 있는 ID만 사용하고, 추천 순서대로 전달한다.
                """.formatted(RECOMMEND_SIZE, RECOMMEND_SIZE);

        String result = matchRecommendChatClient.prompt()
                .system(systemPrompt)
                .user(toPromptText(me, candidates))
                .call()
                .content();

        System.out.println("debug >>>> match recommend ai agent result : " + result);

        // 5. 후보에 있는 ID만, 중복 없이, 상위 3개
        return pick(toRecommend(result), candidates);
    }

    // 선호 종목이거나 같은 자치구 + 성별이 맞는 매치, 시작이 가까운 순 (AI 호출 없음)
    List<MatchResponseDto> candidates(UserInfoResponseDto me, List<MatchResponseDto> matches) {
        List<SportType> sports = me.getPreferenceSports() == null ? List.of() : me.getPreferenceSports();
        String district = me.getDistrict() == null ? null : me.getDistrict().getName();

        return matches.stream()
                .filter(match -> sports.contains(match.getSportType())
                        || (district != null && district.equals(match.getRegion())))
                .filter(match -> isGenderAllowed(me.getGender(), match.getGenderGroup()))
                .sorted(Comparator.comparing(MatchResponseDto::getStartAt))
                .toList();
    }

    // 혼성은 누구나, 남성/여성 전용은 같은 성별만
    private boolean isGenderAllowed(Gender gender, GenderGroup genderGroup) {
        if (genderGroup == GenderGroup.MIXED) {
            return true;
        }
        return gender != null && genderGroup != null && genderGroup.name().equals(gender.name());
    }

    private String toPromptText(UserInfoResponseDto me, List<MatchResponseDto> candidates) {
        String sports = me.getPreferenceSports() == null ? "" : me.getPreferenceSports().stream()
                .map(SportType::getDescription)
                .collect(Collectors.joining(", "));

        String matches = candidates.stream()
                .map(match -> "- id=%d | %s | %s | %s | %s | %d/%d명 | %s".formatted(
                        match.getMatchId(),
                        match.getSportType().getDescription(),
                        match.getRegion(),
                        match.getStartAt(),
                        match.getGenderGroup().getDescription(),
                        match.getNumCurrentParticipant(), match.getMaxParticipant(),
                        match.getTitle()))
                .collect(Collectors.joining("\n"));

        return """
                ## 회원 정보
                - 활동 자치구: %s
                - 선호 종목: %s

                ## 후보 매치 (id | 종목 | 자치구 | 시작 일시 | 성별 구성 | 인원 | 제목)
                %s
                """.formatted(
                        me.getDistrict() == null ? "미설정" : me.getDistrict().getName(),
                        sports.isBlank() ? "없음" : sports,
                        matches);
    }

    // tool이 호출되면 ID 목록 JSON, 호출되지 않으면 AI의 안내 문장이 온다.
    private AiRecommendDto toRecommend(String result) {
        if (result == null || result.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(result, AiRecommendDto.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private List<MatchResponseDto> pick(AiRecommendDto recommend, List<MatchResponseDto> candidates) {
        if (recommend == null || recommend.getMatchIds() == null) {
            throw new AiRecommendException(FAIL_MESSAGE);
        }
        Map<Long, MatchResponseDto> byId = candidates.stream()
                .collect(Collectors.toMap(MatchResponseDto::getMatchId, Function.identity()));

        List<MatchResponseDto> picked = new ArrayList<>(recommend.getMatchIds().stream()
                .distinct()
                .map(byId::get)
                .filter(Objects::nonNull)
                .limit(RECOMMEND_SIZE)
                .toList());

        if (picked.isEmpty()) {
            throw new AiRecommendException(FAIL_MESSAGE);
        }

        // AI가 3개보다 적게 고르면 나머지는 후보 순서(시작이 가까운 순)로 채운다.
        for (MatchResponseDto candidate : candidates) {
            if (picked.size() >= RECOMMEND_SIZE) {
                break;
            }
            if (!picked.contains(candidate)) {
                picked.add(candidate);
            }
        }
        return picked;
    }
}
