package com.example.sporty.features.users.domain.entity;

import lombok.Getter;

@Getter
public enum UserStatus {

    ACTIVE("정상 회원"),
    WITHDRAWAL_PENDING("탈퇴 처리 중"),
    WITHDRAWN("탈퇴 회원");

    private final String description;

    UserStatus(String description) {
        this.description = description;
    }

}
