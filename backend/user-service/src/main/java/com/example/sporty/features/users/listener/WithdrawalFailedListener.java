package com.example.sporty.features.users.listener;

import com.example.sporty.features.users.domain.dto.WithdrawalFailedEvent;
import com.example.sporty.features.users.service.WithdrawalCompensationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WithdrawalFailedListener {

    private final ObjectMapper objectMapper;
    private final WithdrawalCompensationService compensationService;

    @KafkaListener(
            topics = "match.withdrawal.failed",
            groupId = "user-service"
    )
    public void receive(String payload) throws JsonProcessingException {
        WithdrawalFailedEvent event = objectMapper.readValue(
                payload, WithdrawalFailedEvent.class
        );
        // 실패 보상은 회원 상태만 복원한다. 기존 Refresh Token은 보존한다.
        // 예외는 공통 Kafka 오류 처리기로 전달해 재시도 및 DLT 처리한다.
        compensationService.compensate(event);
    }
}
