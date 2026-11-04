package com.example.sporty.features.exerciseMatching.withdrawal;

import java.time.LocalDateTime;
import java.util.List;

public record WithdrawalCompletedEvent(String eventId,
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime occurredAt, String sagaId,
        Long userId, List<Long> deletedMatchIds, List<Long> leftMatchIds) {
    public static final String TOPIC = "match.withdrawal.completed";
}
