package com.example.sporty.features.commons.exception.matches;

public class MatchUserNotFoundException extends RuntimeException {
    public MatchUserNotFoundException() {
        super("유저 ID에 해당하는 행을 찾을 수 없습니다.");
    }
}
