package com.example.sporty.features.users.domain.dto;

import java.util.List;

public record WithdrawalCompletedEvent(
        String eventId,
        String occurredAt,
        String sagaId,
        Long userId,
        List<Long> deletedMatchIds,
        List<Long> leftMatchIds
) {
}