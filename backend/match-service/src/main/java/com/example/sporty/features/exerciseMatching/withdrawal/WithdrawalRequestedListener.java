package com.example.sporty.features.exerciseMatching.withdrawal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WithdrawalRequestedListener {
    private final ObjectMapper mapper;
    private final WithdrawalService service;

    @KafkaListener(topics = WithdrawalRequestedEvent.TOPIC, groupId = "match-service",
            containerFactory = "withdrawalKafkaListenerContainerFactory")
    public void consume(String payload, Acknowledgment acknowledgment) throws JsonProcessingException {
        var event = mapper.readValue(payload, WithdrawalRequestedEvent.class);
        log.info("[KAFKA] withdrawal received eventId={} userId={}", event.eventId(), event.userId());
        service.handle(event);
        acknowledgment.acknowledge(); // DB commit, or deliberate duplicate/stale ignore, completed.
    }
}
