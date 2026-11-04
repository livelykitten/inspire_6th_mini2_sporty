package com.example.sporty.features.users.controller;

import com.example.sporty.features.users.domain.dto.UserProfileResponseDto;
import com.example.sporty.features.users.domain.dto.UserStatusResponseDto;
import com.example.sporty.features.users.service.InternalUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final InternalUserService internalUserService;

    @GetMapping("/{userId}/status")
    public UserStatusResponseDto getStatus(
            @PathVariable("userId") Long userId
    ) {
        return internalUserService.getStatus(userId);
    }

    @GetMapping("/profiles")
    public List<UserProfileResponseDto> getProfiles(
            @RequestParam(name = "userIds") List<Long> userIds
    ) {
        return internalUserService.getProfiles(userIds);
    }
}