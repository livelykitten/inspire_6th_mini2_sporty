package com.example.sporty.features.ai.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import com.example.sporty.features.ai.client.dto.UserInfoResponseDto;

@FeignClient(name = "user-service")
public interface UserClient {

    @GetMapping("/api/users/me")
    UserInfoResponseDto getMyInfo(@RequestHeader("X-User-Id") Long userId);
}
