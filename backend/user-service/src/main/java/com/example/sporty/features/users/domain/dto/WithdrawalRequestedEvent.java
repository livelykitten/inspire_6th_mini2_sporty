package com.example.sporty.features.users.domain.dto;

public record WithdrawalRequestedEvent(
        String eventId,
        String occurredAt,
        String sagaId,
        Long userId
) {
}