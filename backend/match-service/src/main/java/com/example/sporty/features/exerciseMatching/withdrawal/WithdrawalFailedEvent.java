package com.example.sporty.features.exerciseMatching.withdrawal;

import java.time.LocalDateTime;

/** EV-04 is recorded only after a confirmed cleanup rollback and guarded outcome checks. */
public record WithdrawalFailedEvent(String eventId,
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime occurredAt, String sagaId,
        Long userId, String reason) {
    public static final String TOPIC = "match.withdrawal.failed";
}
