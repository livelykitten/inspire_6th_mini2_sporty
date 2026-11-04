package com.example.sporty.features.commons.exception.exerciseMatching;

public class MatchParticipantNotFoundException extends RuntimeException {

    public MatchParticipantNotFoundException() {
        super("매치 참가 정보를 찾을 수 없습니다.");
    }
}
