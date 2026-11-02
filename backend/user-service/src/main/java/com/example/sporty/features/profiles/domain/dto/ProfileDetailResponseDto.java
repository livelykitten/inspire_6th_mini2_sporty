package com.example.sporty.features.profiles.domain.dto;

import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.profiles.domain.entity.District;
import com.example.sporty.features.profiles.domain.entity.ProfileEntity;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class ProfileDetailResponseDto {

        private Long id;
        private String nickname;
        private District district;
        private String imageUrl;
        private List<SportType> preferenceSports;

    public static ProfileDetailResponseDto fromEntity(ProfileEntity entity, List<SportType> preferenceSports) {
        return ProfileDetailResponseDto.builder()
                .id(entity.getId())
                .nickname(entity.getNickname())
                .district(entity.getDistrict())
                .imageUrl(entity.getImageUrl())
                .preferenceSports(preferenceSports)
                .build();

    }
}
