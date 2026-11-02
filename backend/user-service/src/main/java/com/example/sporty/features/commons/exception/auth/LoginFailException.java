package com.example.sporty.features.commons.exception.auth;

public class LoginFailException extends RuntimeException {
    public LoginFailException() {
        super("이메일 또는 비밀번호를 확인해주세요.");
    }
}
