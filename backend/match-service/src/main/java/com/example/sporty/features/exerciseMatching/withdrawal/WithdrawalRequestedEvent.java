package com.example.sporty.features.exerciseMatching.withdrawal;

import java.time.LocalDateTime;
import java.util.UUID;

public record WithdrawalRequestedEvent(String eventId,
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime occurredAt,
        String sagaId, Long userId) {
    public static final String TOPIC = "user.withdrawal.requested";

    public void validate() {
        if (eventId == null || sagaId == null || occurredAt == null || userId == null || userId <= 0) {
            throw new IllegalArgumentException("Invalid EV-02 envelope");
        }
        UUID.fromString(eventId);
        UUID.fromString(sagaId);
    }
}
