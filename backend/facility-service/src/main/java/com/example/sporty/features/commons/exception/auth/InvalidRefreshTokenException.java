package com.example.sporty.features.commons.exception.auth;

public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("유효하지 않은 Refresh Token입니다. 다시 로그인해주세요.");
    }
}
