package com.example.sporty.features.exerciseMatching.domain.dto;

import com.example.sporty.features.exerciseMatching.client.MemberProfile;

public record UserProfileResponseDto (
    Long userId,
    Long profileId,
    String nickname,
    String imageUrl
) {

    public MemberProfile toMemberProfile() {
        return new MemberProfile(profileId, nickname, imageUrl);
    }
}
