package com.example.sporty.features.exerciseMatching.domain.dto;

public record UserStatusResponseDto (
    Long userId,
    String status,
    String sagaId
) {}
