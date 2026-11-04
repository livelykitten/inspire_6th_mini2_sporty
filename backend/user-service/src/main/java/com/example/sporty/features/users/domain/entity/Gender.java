package com.example.sporty.features.users.domain.entity;

import lombok.Getter;

@Getter
public enum Gender {

    MALE("남성"),
    FEMALE("여성");

    private final String description;

    Gender(String description) {
        this.description = description;
    }

}
