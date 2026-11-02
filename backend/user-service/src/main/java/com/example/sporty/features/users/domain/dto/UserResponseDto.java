package com.example.sporty.features.users.domain.dto;

import com.example.sporty.features.users.domain.entity.Gender;
import com.example.sporty.features.users.domain.entity.UserEntity;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class UserResponseDto {

    private Long id;
    private String email;
    private Gender gender;

    public static UserResponseDto fromEntity(UserEntity entity) {
        return UserResponseDto.builder()
                .id(entity.getId())
                .email(entity.getEmail())
                .gender(entity.getGender())
                .build();
    }
}
