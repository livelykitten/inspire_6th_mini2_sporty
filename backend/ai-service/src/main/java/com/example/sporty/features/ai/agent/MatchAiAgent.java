package com.example.sporty.features.ai.agent;

import java.time.LocalDate;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.example.sporty.features.ai.domain.dto.AiSearchConditionDto;
import com.example.sporty.features.ai.domain.dto.AiSearchResponseDto;
import com.example.sporty.features.ai.util.PromptDateTable;
import com.example.sporty.features.ai.util.SupportedSports;
import com.example.sporty.features.commons.exception.ai.AiSearchException;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchResponseDto;
import com.example.sporty.features.exerciseMatching.service.MatchService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;


@Service 
public class MatchAiAgent {
    
    private static final String NO_CONDITION_MESSAGE =
            "검색 조건을 찾지 못했습니다. 종목, 지역, 날짜 중 하나 이상을 포함해 주세요. (예: 이번 주말 강남에서 풋살)";

    private final ChatClient matchChatClient;
    private final MatchService matchService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    // ChatClient 빈이 2개라 이름으로 지정한다 (AiAgentConfig의 @Bean 메서드명)
    public MatchAiAgent(@Qualifier("matchChatClient") ChatClient matchChatClient, MatchService matchService) {
        this.matchChatClient = matchChatClient;
        this.matchService = matchService;
    }

    public AiSearchResponseDto search(String prompt) {
        AiSearchConditionDto condition = extractCondition(prompt);
        List<MatchResponseDto> matches = matchService.searchMatches(condition.toSearchRequest());

        System.out.println("debug >>>> match ai agent search matches : " + matches.size());

        return AiSearchResponseDto.builder()
                .conditions(condition.toConditions())
                .matches(matches)
                .criteria(condition)
                .build();
    }

    // AI 호출 → 조건 추출, 조건이 없으면 AiSearchException
    AiSearchConditionDto extractCondition(String prompt) {
        System.out.println("debug >>>> match ai agent search : " + prompt);

        LocalDate today = PromptDateTable.today();

        String systemPrompt = """
                당신은 운동 매칭 서비스의 검색 에이전트입니다.

                ## 규칙
                1. 사용자의 문장에서 검색 조건을 추출해 searchMatches 도구를 호출한다.
                2. 문장에 없는 조건은 채우지 않는다. 추측하지 않는다.
                3. 지역은 서울시 자치구 이름으로 변환한다. 예: 강남역 → 강남구, 잠실 → 송파구
                4. 날짜는 직접 계산하지 않고 아래 날짜표에서 찾아 사용한다.
                5. 기간 표현은 startAt을 시작일 00:00, endAt을 종료일 23:59로 한다.
                6. 성별 구성은 남자만 → MALE, 여자만 → FEMALE, 남녀 함께·혼성 → MIXED로 변환한다.

                ## 날짜표
                %s
                """.formatted(PromptDateTable.of(today));

        String result;
        try {
            result = matchChatClient.prompt()
                    .system(systemPrompt)
                    .user(prompt)
                    .call()
                    .content();
        } catch (IllegalArgumentException e) {
            // AI가 enum에 없는 값(예: SQUASH)을 넘기면 도구 인자 변환 단계에서 실패한다.
            throw new AiSearchException(SupportedSports.unsupportedMessage());
        }

        System.out.println("debug >>>> match ai agent search result : " + result);
        
        AiSearchConditionDto condition = toCondition(result);
        if (condition == null || hasNoCondition(condition)) {
            throw new AiSearchException(NO_CONDITION_MESSAGE);
        }
        return condition;
    }

    // tool이 호출되면 조건 JSON, 호출되지 않으면 AI의 안내 문장이 온다.
    private AiSearchConditionDto toCondition(String result) {
        if (result == null || result.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(result, AiSearchConditionDto.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    // tool은 호출됐지만 추출된 조건이 하나도 없는 경우
    private boolean hasNoCondition(AiSearchConditionDto condition) {
        return condition.getSportType() == null
                && condition.getRegion() == null
                && condition.getStartAt() == null
                && condition.getEndAt() == null
                && condition.getSkillLevel() == null
                && condition.getGenderGroup() == null
                && condition.getStatus() == null;
    }
}
