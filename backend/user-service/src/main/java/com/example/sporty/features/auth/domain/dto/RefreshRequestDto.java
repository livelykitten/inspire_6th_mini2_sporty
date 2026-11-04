package com.example.sporty.features.auth.domain.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RefreshRequestDto {
    private String refreshToken;
}
