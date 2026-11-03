package com.example.sporty.features.users.controller;

import com.example.sporty.features.commons.exception.profiles.ProfileIntegrityException;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice(assignableTypes = InternalUserController.class)
public class InternalUserExceptionHandler {

    public record InternalErrorResponse(String message, String code) {
    }

    @ExceptionHandler(ProfileIntegrityException.class)
    public ResponseEntity<InternalErrorResponse> handleProfileIntegrity(
            ProfileIntegrityException exception
    ) {
        return ResponseEntity.internalServerError()
                .body(new InternalErrorResponse(
                        exception.getMessage(),
                        null
                ));
    }
}