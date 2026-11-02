package com.example.sporty.features.ai.tools;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.example.sporty.features.ai.domain.dto.AiSearchConditionDto;
import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;
import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;

@Component 
public class MatchAiTool {
    
    @Tool(description = "종목, 지역, 날짜, 실력, 성별 구성, 모집 상태 조건으로 운동 매치를 검색한다.",
        returnDirect = true)
    public AiSearchConditionDto searchMatches(
            @ToolParam(required = false, description = "운동 종목") SportType sportType,
            @ToolParam(required = false, description = "서울시 자치구 이름. 예: 강남구, 송파구") String region,
            @ToolParam(required = false, description = "검색 시작 일시") LocalDateTime startAt,
            @ToolParam(required = false, description = "검색 종료 일시") LocalDateTime endAt,
            @ToolParam(required = false, description = "실력 수준") SkillLevel skillLevel,
            @ToolParam(required = false, description = "참가자 성별 구성") GenderGroup genderGroup,
            @ToolParam(required = false, description = "모집 상태") MatchStatus status) {

        AiSearchConditionDto condition = AiSearchConditionDto.builder()
                .sportType(sportType)
                .region(region)
                .startAt(startAt)
                .endAt(endAt)
                .skillLevel(skillLevel)
                .genderGroup(genderGroup)
                .status(status)
                .build();

        System.out.println("debug >>>> ai tool searchMatches condition : " + condition);

        return condition;
    }
}
