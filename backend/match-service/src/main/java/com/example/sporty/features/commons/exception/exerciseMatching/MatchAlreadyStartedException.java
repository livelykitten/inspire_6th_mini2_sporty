package com.example.sporty.features.commons.exception.exerciseMatching;

public class MatchAlreadyStartedException extends RuntimeException {

    public MatchAlreadyStartedException() {
        super("이미 시작된 매치에는 참여할 수 없습니다.");
    }
}
