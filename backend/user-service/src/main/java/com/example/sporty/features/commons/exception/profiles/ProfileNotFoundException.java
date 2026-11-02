package com.example.sporty.features.commons.exception.profiles;

public class ProfileNotFoundException extends RuntimeException {
    public ProfileNotFoundException() {
        super("존재하지 않는 프로필입니다.");
    }
}
