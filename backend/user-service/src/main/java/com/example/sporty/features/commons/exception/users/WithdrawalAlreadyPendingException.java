package com.example.sporty.features.commons.exception.users;

public class WithdrawalAlreadyPendingException extends RuntimeException {

    public WithdrawalAlreadyPendingException() {
        super("회원 탈퇴가 이미 진행 중입니다.");
    }
}