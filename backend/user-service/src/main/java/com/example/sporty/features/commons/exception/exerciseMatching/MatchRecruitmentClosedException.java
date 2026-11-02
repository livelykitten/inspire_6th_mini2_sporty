package com.example.sporty.features.commons.exception.exerciseMatching;

public class MatchRecruitmentClosedException extends RuntimeException {

    public MatchRecruitmentClosedException() {
        super("모집이 마감된 매치입니다.");
    }
}
