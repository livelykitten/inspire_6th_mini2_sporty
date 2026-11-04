package com.example.sporty.features.commons.exception.exerciseMatching;

public class MatchAlreadyJoinedException extends RuntimeException {

    public MatchAlreadyJoinedException() {
        super("이미 참가 중인 매치입니다.");
    }
}
