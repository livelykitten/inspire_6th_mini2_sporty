package com.example.sporty.features.ai.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.sporty.features.ai.client.dto.MatchResponseDto;
import com.example.sporty.features.ai.client.dto.MatchSearchRequestDto;

// match-service로 api 요청
@FeignClient(name = "match-service", url = "${clients.match-service.url:}")
public interface MatchClient {

    @GetMapping("/api/matches")
    List<MatchResponseDto> searchMatches(@SpringQueryMap MatchSearchRequestDto request);
}
