package com.example.sporty.features.users.domain.dto;

import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.profiles.domain.entity.District;
import com.example.sporty.features.users.domain.entity.Gender;
import com.example.sporty.features.users.domain.entity.UserEntity;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserSignUpRequestDto {

        @Email(message = "이메일 형식을 지켜주세요.")
        @NotBlank
        @Size(max = 50)
        private String email;

        @NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
        @Size(min = 8, max = 20)
        private String password;

        @NotNull
        private Gender gender;

        @NotBlank(message = "닉네임은 필수 입력 항목입니다.")
        @Size(max = 50)
        private String nickname;

        @NotNull
        private District district;

        @NotNull(message = "선호 종목 목록은 필수입니다. 선택하지 않으면 빈 목록을 보내주세요.")
        private List<@NotNull(message = "선호 종목은 null일 수 없습니다.") SportType> sportTypes;

        public static UserEntity toUserEntity(UserSignUpRequestDto request, String encodedPassword) {
            return UserEntity.builder()
                    .email(request.getEmail())
                    .password(encodedPassword)
                    .gender(request.gender)
                    .build();
        }

}
