package com.example.sporty.features.profiles.domain.dto;

import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.profiles.domain.entity.District;
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
public class ProfileUpdateRequestDto {

    @NotBlank(message = "닉네임은 필수 입력 항목입니다.")
    @Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
    private String nickname;

    @NotNull(message = "주 활동 자치구는 필수입니다.")
    private District district;

    @NotNull(message = "선호 종목 목록은 필수입니다. 선택하지 않으면 빈 목록을 보내주세요.")
    private List<@NotNull(message = "선호 종목은 null일 수 없습니다.") SportType> sportTypes;

}
