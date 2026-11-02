package com.example.sporty.features.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import com.example.sporty.features.ai.domain.dto.AiSearchConditionDto;
import com.example.sporty.features.ai.tools.MatchAiTool;
import com.example.sporty.features.commons.exception.ai.AiSearchException;
import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;
import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;


/*
- AI-02 자연어 -> 검색 조건 추출 테스트
- 실제 OpenAI를 호출하므로 OPEN_AI_KEY 환경변수가 없으면 스킵
*/
@EnabledIfEnvironmentVariable(named = "OPEN_AI_KEY", matches = ".+")
class MatchAiAgentTest {
    
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private static MatchAiAgent matchAIAgent;

    @BeforeAll
    static void setUp() {
        OpenAiApi api = OpenAiApi.builder()
                .apiKey(System.getenv("OPEN_AI_KEY"))
                .build();

        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder();
        String model = System.getenv("OPEN_AI_MODEL");
        if (model != null && !model.isBlank()) {
            options.model(model);
        }

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options.build())
                .build();

        // AiAgentConfig와 같은 조립: tool을 등록한 ChatClient
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultTools(new MatchAiTool())
                .build();

        // 조건 추출만 검증하므로 MatchService는 사용하지 않는다.
        matchAIAgent = new MatchAiAgent(chatClient, null);
    }

    private AiSearchConditionDto search(String prompt) {
        return matchAIAgent.extractCondition(prompt);
    }

    private LocalDate today() {
        return LocalDate.now(SEOUL);
    }

    // 이번 주말 시작일: 일요일이면 오늘, 아니면 가장 가까운 토요일
    private LocalDate thisSaturday() {
        return today().getDayOfWeek() == DayOfWeek.SUNDAY
                ? today()
                : today().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
    }

    // 이번 주말 종료일: 오늘 이후 가장 가까운 일요일 (오늘이 일요일이면 오늘)
    private LocalDate thisSunday() {
        return today().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    }

    @Test
    @DisplayName("[TC-AI02-01] 종목, 지역, 실력, 기간(이번 주말)을 모두 추출한다")
    void extractsAllConditions() throws Exception {
        AiSearchConditionDto condition = search("이번 주말 강남에서 풋살 초보 매치 찾아줘");

        assertThat(condition.getSportType()).isEqualTo(SportType.FUTSAL);
        assertThat(condition.getRegion()).isEqualTo("강남구");
        assertThat(condition.getSkillLevel()).isEqualTo(SkillLevel.BEGINNER);
        assertThat(condition.getStartAt().toLocalDate()).isEqualTo(thisSaturday());
        assertThat(condition.getEndAt().toLocalDate()).isEqualTo(thisSunday());
    }

    @Test
    @DisplayName("[TC-AI02-02] 동네 이름을 자치구로 변환한다 (잠실 → 송파구)")
    void convertsNeighborhoodToDistrict() throws Exception {
        AiSearchConditionDto condition = search("잠실에서 농구할 사람");

        assertThat(condition.getSportType()).isEqualTo(SportType.BASKETBALL);
        assertThat(condition.getRegion()).isEqualTo("송파구");
        assertThat(condition.getStartAt()).isNull();
    }

    @Test
    @DisplayName("[TC-AI02-03] 상대 날짜(내일)를 오늘 기준으로 계산한다")
    void calculatesRelativeDate() throws Exception {
        AiSearchConditionDto condition = search("내일 테니스 칠 사람 구해요");

        assertThat(condition.getSportType()).isEqualTo(SportType.TENNIS);
        assertThat(condition.getStartAt().toLocalDate()).isEqualTo(today().plusDays(1));
    }

    @Test
    @DisplayName("[TC-AI02-04] 모집 상태와 실력 수준을 enum으로 추출한다")
    void extractsStatusAndSkillLevel() throws Exception {
        AiSearchConditionDto condition = search("모집 중인 배드민턴 상급 매치 보여줘");

        assertThat(condition.getSportType()).isEqualTo(SportType.BADMINTON);
        assertThat(condition.getSkillLevel()).isEqualTo(SkillLevel.ADVANCED);
        assertThat(condition.getStatus()).isEqualTo(MatchStatus.RECRUITING);
        assertThat(condition.getRegion()).isNull();
    }

    @Test
    @DisplayName("[TC-AI02-05] 검색 조건이 없으면 안내 문구와 함께 AiSearchException을 던진다")
    void throwsWhenNoCondition() {
        AiSearchException e = assertThrows(AiSearchException.class,
                () -> matchAIAgent.extractCondition("같이 운동할 사람 있나요"));

        assertThat(e.getMessage()).startsWith("검색 조건을 찾지 못했습니다.");
    }

    @Test
    @DisplayName("[TC-AI02-09] 성별 구성을 enum으로 추출한다 (여자끼리 → FEMALE)")
    void extractsGenderGroup() throws Exception {
        AiSearchConditionDto condition = search("여자끼리 배드민턴 칠 사람");

        assertThat(condition.getSportType()).isEqualTo(SportType.BADMINTON);
        assertThat(condition.getGenderGroup()).isEqualTo(GenderGroup.FEMALE);
    }
}
