package com.example.sporty.features.exerciseMatching.client;

import java.util.List;

import org.springframework.cloud.openfeign.CollectionFormat;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.sporty.features.exerciseMatching.domain.dto.UserProfileResponseDto;
import com.example.sporty.features.exerciseMatching.domain.dto.UserStatusResponseDto;


@FeignClient (
    name="user-service",
    fallbackFactory = UserServiceFallbackFactory.class
)
public interface UserServiceClient {
    @GetMapping ("/internal/users/{userId}/status")
    public UserStatusResponseDto getUserStatus(@PathVariable("userId") Long userId);

    @CollectionFormat (feign.CollectionFormat.CSV)
    @GetMapping("/internal/users/profiles")
    public List<UserProfileResponseDto> getUserProfiles(@RequestParam("userIds") List<Long> userIds);
}
