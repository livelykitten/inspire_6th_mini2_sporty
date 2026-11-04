package com.example.sporty.features.commons.exception.matches;

public class WithdrawnUserFoundException extends RuntimeException{
    public WithdrawnUserFoundException() {
        super("탈퇴한 사용자의 매치 API 요청입니다.");
    }
}
