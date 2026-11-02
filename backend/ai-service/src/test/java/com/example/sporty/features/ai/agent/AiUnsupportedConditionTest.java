package com.example.sporty.features.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import com.example.sporty.features.ai.util.SupportedSports;
import com.example.sporty.features.commons.exception.ai.AiDraftException;
import com.example.sporty.features.commons.exception.ai.AiSearchException;
import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.service.MatchService;

/*
- AI가 enum에 없는 종목(예: SQUASH)을 도구에 넘긴 경우의 처리 테스트
- 실제 AI 대신 ChatClient가 도구 인자 변환 예외를 던지도록 흉내 내므로 API 키 없이 실행된다.
*/
class AiUnsupportedConditionTest {

    private static final String PROMPT = "제주도에서 스쿼시 할 사람 찾아줘";

    // Spring AI가 도구 인자를 enum으로 바꾸다 실패할 때 던지는 예외와 같은 형태
    private ChatClient chatClientFailingOnSquash() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenThrow(new IllegalArgumentException(
                        "No enum constant com.example.sporty.features.commons.util.SportType.SQUASH"));
        return chatClient;
    }

    @Test
    @DisplayName("[AI-02] 지원하지 않는 종목으로 검색하면 지원 종목 안내와 함께 AiSearchException이 발생하고 매치를 조회하지 않는다")
    void searchWithUnsupportedSport() {
        MatchService matchService = mock(MatchService.class);
        MatchAiAgent agent = new MatchAiAgent(chatClientFailingOnSquash(), matchService);

        assertThatThrownBy(() -> agent.search(PROMPT))
                .isInstanceOf(AiSearchException.class)
                .hasMessage(SupportedSports.unsupportedMessage());
        verifyNoInteractions(matchService);
    }

    @Test
    @DisplayName("[AI-01] 지원하지 않는 종목으로 초안을 만들면 지원 종목 안내와 함께 AiDraftException이 발생한다")
    void draftWithUnsupportedSport() {
        MatchDraftAiAgent agent = new MatchDraftAiAgent(chatClientFailingOnSquash());

        assertThatThrownBy(() -> agent.draft(PROMPT))
                .isInstanceOf(AiDraftException.class)
                .hasMessage(SupportedSports.unsupportedMessage());
    }

    @Test
    @DisplayName("지원 종목 안내에는 SportType의 모든 종목 이름이 들어간다")
    void messageListsAllSportTypes() {
        String message = SupportedSports.unsupportedMessage();

        for (SportType sportType : SportType.values()) {
            assertThat(message).contains(sportType.getDescription());
        }
    }
}
