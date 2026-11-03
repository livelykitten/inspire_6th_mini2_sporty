package com.example.sporty.features.users.domain.dto;

import com.example.sporty.features.users.domain.entity.UserStatus;

public record UserStatusResponseDto(
        Long userId,
        UserStatus status,
        String sagaId
) {
}