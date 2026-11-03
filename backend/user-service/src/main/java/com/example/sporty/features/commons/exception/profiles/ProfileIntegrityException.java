package com.example.sporty.features.commons.exception.profiles;

public class ProfileIntegrityException extends RuntimeException {

    public ProfileIntegrityException() {
        super("회원 프로필 데이터 무결성 오류입니다.");
    }
}