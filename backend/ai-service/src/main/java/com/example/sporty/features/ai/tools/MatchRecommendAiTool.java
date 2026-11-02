package com.example.sporty.features.ai.tools;

import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.example.sporty.features.ai.domain.dto.AiRecommendDto;

@Component
public class MatchRecommendAiTool {

    @Tool(description = "회원에게 추천할 운동 매치 ID를 추천 순서대로 전달한다.", returnDirect = true)
    public AiRecommendDto recommendMatches(
            @ToolParam(description = "추천 순서대로 정렬한 매치 ID 목록. 후보 목록에 있는 ID만 사용") List<Long> matchIds) {

        AiRecommendDto recommend = AiRecommendDto.builder()
                .matchIds(matchIds)
                .build();

        System.out.println("debug >>>> ai tool recommendMatches : " + recommend);
        return recommend;
    }
}
