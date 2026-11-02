package com.example.sporty.features.exerciseMatching.domain.enums;
import lombok.Getter;

@Getter 
public enum GenderGroup {
    MALE("남성"),
    FEMALE("여성"),
    MIXED("혼성");
    
    private final String description;

    GenderGroup(String description) {
        this.description = description;
    }

}
