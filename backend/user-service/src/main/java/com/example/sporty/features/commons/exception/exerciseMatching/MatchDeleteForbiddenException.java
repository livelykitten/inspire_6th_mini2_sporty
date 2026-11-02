package com.example.sporty.features.commons.exception.exerciseMatching;

public class MatchDeleteForbiddenException extends RuntimeException {

    public MatchDeleteForbiddenException() {
        super("매치 생성자만 삭제할 수 있습니다.");
    }
}
