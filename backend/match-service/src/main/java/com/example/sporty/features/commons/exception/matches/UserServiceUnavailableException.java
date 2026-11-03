package com.example.sporty.features.commons.exception.matches;

public class UserServiceUnavailableException extends RuntimeException {
    public UserServiceUnavailableException() {
        super("	잠시 후 다시 시도해주세요.");
    }
    
    public UserServiceUnavailableException(Throwable cause) {
        super("	잠시 후 다시 시도해주세요: " + cause);
    }
    
}
