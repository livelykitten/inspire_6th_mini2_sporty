package com.example.sporty.features.users.listener;

import com.example.sporty.features.auth.service.RefreshTokenService;
import com.example.sporty.features.users.domain.dto.WithdrawalCompletedEvent;
import com.example.sporty.features.users.service.WithdrawalCompletionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WithdrawalCompletedListener {

    private final ObjectMapper objectMapper;
    private final WithdrawalCompletionService completionService;
    private final RefreshTokenService refreshTokenService;

    @KafkaListener(
            topics = "match.withdrawal.completed",
            groupId = "user-service"
    )
    public void receive(String payload) throws JsonProcessingException {
        WithdrawalCompletedEvent event = objectMapper.readValue(
                payload,
                WithdrawalCompletedEvent.class
        );

        // 별도 서비스의 트랜잭션을 통해 DB 커밋을 완료한다.
        boolean completed = completionService.complete(event);

        // 상태 변경이 없고 현재 탈퇴 회원도 아니면 처리를 종료한다.
        if (!completed && !completionService.isWithdrawn(event.userId())) {
            return;
        }

        // DB 커밋 후 Redis 삭제. 실패하면 예외를 전달해 재시도한다.
        refreshTokenService.delete(event.userId());

        log.info(
                "[KAFKA] 탈퇴 회원 토큰 정리 완료 "
                        + "eventId={} userId={} sagaId={}",
                event.eventId(),
                event.userId(),
                event.sagaId()
        );
    }
}