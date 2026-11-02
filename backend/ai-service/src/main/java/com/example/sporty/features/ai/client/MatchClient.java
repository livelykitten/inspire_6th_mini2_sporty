package com.example.sporty.features.ai.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import com.example.sporty.features.ai.client.dto.MatchResponseDto;
import com.example.sporty.features.ai.client.dto.MatchSearchRequestDto;
import com.example.sporty.features.ai.client.dto.MyMatchResponseDto;

// match-service로 api 요청
@FeignClient(name = "match-service")
public interface MatchClient {

    @GetMapping("/api/matches")
    List<MatchResponseDto> searchMatches(@SpringQueryMap MatchSearchRequestDto request);

    // Gateway 인증으로 받은 사용자 ID를 match-service에 전달한다.
    @GetMapping("/api/matches/me")
    List<MyMatchResponseDto> getMyMatches(@RequestHeader("X-User-Id") Long userId);
}
