package com.example.sporty.features.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import com.example.sporty.features.ai.tools.MatchRecommendAiTool;
import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchResponseDto;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;
import com.example.sporty.features.exerciseMatching.repository.MatchParticipantRepository;
import com.example.sporty.features.exerciseMatching.service.MatchService;
import com.example.sporty.features.profiles.domain.entity.District;
import com.example.sporty.features.users.domain.dto.UserInfoResponseDto;
import com.example.sporty.features.users.domain.entity.Gender;
import com.example.sporty.features.users.service.UserService;

/*
- AI-03 매치 추천 테스트 (테스트 케이스 명세 AI-005 ~ AI-007)
  - TC-AI03-01, 02 → AI-005 사용자 정보 기반 운동 매칭 추천
  - TC-AI03-03     → AI-006 사용자 정보 일부가 없는 경우 추천
  - TC-AI03-04     → AI-007 추천 가능한 매칭이 없는 경우
- 회원 정보와 매치 조회는 Mock으로 대체하므로 DB 없이 실행된다.
- TC-AI03-01만 실제 OpenAI를 호출하므로 OPEN_AI_KEY 환경변수가 없으면 스킵
*/
@ExtendWith(MockitoExtension.class)
class MatchRecommendAiAgentTest {

    private static final LocalDateTime BASE = LocalDateTime.now().plusDays(7).withNano(0);

    @Mock
    private UserService userService;

    @Mock
    private MatchService matchService;

    @Mock
    private MatchParticipantRepository matchParticipantRepository;

    @Mock
    private ChatClient chatClient;

    private MatchRecommendAiAgent agent;

    @BeforeEach
    void setUp() {
        agent = new MatchRecommendAiAgent(chatClient, userService, matchService, matchParticipantRepository);
    }

    private UserInfoResponseDto me(Gender gender, District district, List<SportType> sports) {
        return UserInfoResponseDto.builder()
                .gender(gender).district(district).preferenceSports(sports)
                .build();
    }

    private MatchResponseDto match(long id, SportType sportType, String region, GenderGroup genderGroup, int hours) {
        return MatchResponseDto.builder()
                .matchId(id).title("매치 " + id)
                .sportType(sportType).region(region).genderGroup(genderGroup)
                .startAt(BASE.plusHours(hours)).endAt(BASE.plusHours(hours + 2))
                .numCurrentParticipant(1).maxParticipant(10)
                .status(MatchStatus.RECRUITING)
                .build();
    }

    // AiAgentConfig와 같은 조립: 추천 tool을 등록한 ChatClient
    private ChatClient openAiChatClient() {
        OpenAiApi api = OpenAiApi.builder().apiKey(System.getenv("OPEN_AI_KEY")).build();
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder();
        String model = System.getenv("OPEN_AI_MODEL");
        if (model != null && !model.isBlank()) {
            options.model(model);
        }
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api).defaultOptions(options.build()).build();
        return ChatClient.builder(chatModel).defaultTools(new MatchRecommendAiTool()).build();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPEN_AI_KEY", matches = ".+")
    @DisplayName("[TC-AI03-01] AI가 회원 정보로 추천 매치 3개를 고르고, 종목과 자치구가 모두 맞는 매치를 먼저 추천한다")
    void recommendsThreeMatchesWithAi() {
        MatchRecommendAiAgent aiAgent = new MatchRecommendAiAgent(
                openAiChatClient(), userService, matchService, matchParticipantRepository);

        when(userService.getMyInfo(1L)).thenReturn(me(Gender.FEMALE, District.GANGNAM, List.of(SportType.FUTSAL)));
        when(matchService.searchMatches(any())).thenReturn(List.of(
                match(1, SportType.FUTSAL, "마포구", GenderGroup.MIXED, 1),      // 종목
                match(2, SportType.BASKETBALL, "강남구", GenderGroup.MIXED, 2),  // 자치구
                match(3, SportType.FUTSAL, "강남구", GenderGroup.MIXED, 3),      // 종목 + 자치구
                match(4, SportType.TENNIS, "송파구", GenderGroup.MIXED, 4)));    // 둘 다 아님 → 후보 아님
        when(matchParticipantRepository.existsByMatch_IdAndUserId(anyLong(), eq(1L))).thenReturn(false);

        List<MatchResponseDto> result = aiAgent.recommend(1L);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getMatchId()).isEqualTo(3L);
        assertThat(result).extracting(MatchResponseDto::getMatchId).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    @DisplayName("[TC-AI03-02] 선호 종목이거나 같은 자치구이면서 성별이 맞는 매치만 시작이 가까운 순으로 후보가 된다")
    void candidatesMatchProfile() {
        List<MatchResponseDto> matches = List.of(
                match(1, SportType.FUTSAL, "마포구", GenderGroup.MIXED, 3),      // 종목
                match(2, SportType.BASKETBALL, "강남구", GenderGroup.MIXED, 1),  // 자치구
                match(3, SportType.TENNIS, "마포구", GenderGroup.MIXED, 2),      // 둘 다 아님 → 제외
                match(4, SportType.FUTSAL, "강남구", GenderGroup.MALE, 4),       // 남성 전용 → 제외
                match(5, SportType.FUTSAL, "강남구", GenderGroup.FEMALE, 5));    // 여성 전용 → 포함

        List<MatchResponseDto> result = agent.candidates(
                me(Gender.FEMALE, District.GANGNAM, List.of(SportType.FUTSAL)), matches);

        assertThat(result).extracting(MatchResponseDto::getMatchId).containsExactly(2L, 1L, 5L);
    }

    @Test
    @DisplayName("[TC-AI03-03] 선호 종목이나 활동 자치구가 없어도 남은 정보로 후보를 고른다")
    void candidatesWithPartialProfile() {
        List<MatchResponseDto> matches = List.of(
                match(1, SportType.FUTSAL, "마포구", GenderGroup.MIXED, 1),
                match(2, SportType.BASKETBALL, "강남구", GenderGroup.MIXED, 2));

        // 선호 종목 없음 → 자치구로만
        assertThat(agent.candidates(me(Gender.MALE, District.GANGNAM, List.of()), matches))
                .extracting(MatchResponseDto::getMatchId).containsExactly(2L);
        // 활동 자치구 없음 → 선호 종목으로만
        assertThat(agent.candidates(me(Gender.MALE, null, List.of(SportType.FUTSAL)), matches))
                .extracting(MatchResponseDto::getMatchId).containsExactly(1L);
    }

    @Test
    @DisplayName("[TC-AI03-04] 추천할 매치가 없으면 AI를 호출하지 않고 빈 목록을 반환한다")
    void returnsEmptyWithoutAiWhenNoCandidate() {
        when(userService.getMyInfo(1L)).thenReturn(me(Gender.MALE, District.GANGNAM, List.of(SportType.FUTSAL)));
        when(matchService.searchMatches(any())).thenReturn(List.of(
                match(1, SportType.TENNIS, "마포구", GenderGroup.MIXED, 1)));

        assertThat(agent.recommend(1L)).isEmpty();
        verifyNoInteractions(chatClient);
    }
}
