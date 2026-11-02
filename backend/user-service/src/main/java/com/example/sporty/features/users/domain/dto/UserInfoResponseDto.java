package com.example.sporty.features.users.domain.dto;

import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.profiles.domain.entity.District;
import com.example.sporty.features.profiles.domain.entity.ProfileEntity;
import com.example.sporty.features.users.domain.entity.Gender;
import com.example.sporty.features.users.domain.entity.UserEntity;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class UserInfoResponseDto {

    private String email;
    private Gender gender;
    private String nickname;
    private District district;
    private String imageUrl;
    private List<SportType> preferenceSports;


    public static UserInfoResponseDto fromEntity(UserEntity userEntity, ProfileEntity profileEntity, List<SportType> preferenceSports) {
        return UserInfoResponseDto.builder()
                .email(userEntity.getEmail())
                .gender(userEntity.getGender())
                .nickname(profileEntity.getNickname())
                .district(profileEntity.getDistrict())
                .imageUrl(profileEntity.getImageUrl())
                .preferenceSports(preferenceSports)
                .build();

    }

}
