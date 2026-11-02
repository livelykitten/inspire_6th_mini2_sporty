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
@FeignClient(name = "match-service", url = "${clients.match-service.url:}")
public interface MatchClient {

    @GetMapping("/api/matches")
    List<MatchResponseDto> searchMatches(@SpringQueryMap MatchSearchRequestDto request);

    //이부분 나중에 api-gateway 인증방식에 따라 사용자 정보를 받아와야 함
    //일단은 기존 인증에 받은 값을 사용
    @GetMapping("/api/matches/me")
    List<MyMatchResponseDto> getMyMatches(@RequestHeader("X-User-Id") Long userId);
}
