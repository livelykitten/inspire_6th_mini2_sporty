package com.example.sporty.features.commons.exception.exerciseMatching;

public class MatchModifyForbiddenException extends RuntimeException {

    public MatchModifyForbiddenException() {
        super("매치 생성자만 수정할 수 있습니다.");
    }
}
