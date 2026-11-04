package com.example.sporty.features.commons.exception.users;

public class UserNotFoundException extends RuntimeException{

    public UserNotFoundException() {
        super("존재하지 않는 회원입니다.");
    }
}
