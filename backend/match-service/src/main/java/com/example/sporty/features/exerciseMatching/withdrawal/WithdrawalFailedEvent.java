package com.example.sporty.features.exerciseMatching.withdrawal;

import java.time.LocalDateTime;

/** W-32 contract only. Not emitted by the W-22 success path. */
public record WithdrawalFailedEvent(String eventId,
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime occurredAt, String sagaId,
        Long userId, String reason) {
    public static final String TOPIC = "match.withdrawal.failed";
}
