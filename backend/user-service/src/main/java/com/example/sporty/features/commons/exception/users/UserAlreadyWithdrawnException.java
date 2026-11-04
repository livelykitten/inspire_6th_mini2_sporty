package com.example.sporty.features.commons.exception.users;

public class UserAlreadyWithdrawnException extends RuntimeException {

    public UserAlreadyWithdrawnException() {
        super("이미 탈퇴한 회원입니다.");
    }
}