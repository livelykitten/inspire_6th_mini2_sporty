package com.example.sporty.features.users.domain.dto;

public record UserProfileResponseDto(
        Long userId,
        Long profileId,
        String nickname,
        String imageUrl
) {
}