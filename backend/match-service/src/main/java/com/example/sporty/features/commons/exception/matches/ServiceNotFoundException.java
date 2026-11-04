package com.example.sporty.features.commons.exception.matches;

public class ServiceNotFoundException extends RuntimeException {
    public ServiceNotFoundException() {
        super("체육서비스를 찾을 수 없습니다.");
    }
}
