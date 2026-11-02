package com.example.sporty.features.commons.handler;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ErrorResponse {

    private String message;
    private String code;

}
