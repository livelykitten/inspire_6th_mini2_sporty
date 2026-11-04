package com.example.sporty.features.users.domain.dto;

public record WithdrawalFailedEvent(
        String eventId,
        String occurredAt,
        String sagaId,
        Long userId,
        String reason
) {
}
