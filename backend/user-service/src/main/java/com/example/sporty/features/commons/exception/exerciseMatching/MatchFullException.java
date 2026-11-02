package com.example.sporty.features.commons.exception.exerciseMatching;

public class MatchFullException extends RuntimeException {

    public MatchFullException() {
        super("매치 정원이 가득 찼습니다.");
    }
}
