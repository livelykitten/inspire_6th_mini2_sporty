package com.example.sporty.features.users.controller;

import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice(assignableTypes = InternalUserController.class)
public class InternalUserExceptionHandler {

    public record InternalErrorResponse(String message, String code) {
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<InternalErrorResponse> handleIllegalState(
            IllegalStateException exception
    ) {
        return ResponseEntity.internalServerError()
                .body(new InternalErrorResponse(
                        "회원 프로필 데이터 무결성 오류입니다.",
                        null
                ));
    }
}