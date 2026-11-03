package com.example.sporty.features.ai.client.dto;

import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.ai.client.enums.District;
import com.example.sporty.features.ai.client.enums.Gender;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

import java.util.List;


//user-service에서 받는 성별·활동 지역·선호 종목 등
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserInfoResponseDto {

    private String email;
    private Gender gender;
    private String nickname;
    private District district;
    private String imageUrl;
    private List<SportType> preferenceSports;


}
