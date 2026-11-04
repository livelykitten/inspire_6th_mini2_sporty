package com.example.sporty.features.auth.domain.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RefreshResponseDto {
    private String accessToken;
}
