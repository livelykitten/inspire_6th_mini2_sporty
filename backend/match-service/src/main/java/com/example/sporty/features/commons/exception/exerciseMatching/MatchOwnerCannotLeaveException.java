package com.example.sporty.features.commons.exception.exerciseMatching;

public class MatchOwnerCannotLeaveException extends RuntimeException {

    public MatchOwnerCannotLeaveException() {
        super("매치 생성자는 탈퇴할 수 없습니다. 매치 삭제 기능을 이용해주세요.");
    }
}
