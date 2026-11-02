package com.example.sporty.features.commons.exception.exerciseMatching;

public class MatchNotFoundException extends RuntimeException {

    public MatchNotFoundException() {
        super("매치를 찾을 수 없습니다.");
    }
}
