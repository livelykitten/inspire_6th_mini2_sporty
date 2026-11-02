package com.example.sporty.features.profiles.controller;

import com.example.sporty.features.profiles.domain.dto.ProfileDetailResponseDto;
import com.example.sporty.features.profiles.domain.dto.ProfileUpdateRequestDto;
import com.example.sporty.features.profiles.domain.dto.ProfileUpdateResponseDto;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.example.sporty.features.profiles.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/profiles")
public class ProfileController {

    private final ProfileService profileService;

    // [PR-01] 프로필 수정
    @PutMapping("/me")
    public ResponseEntity<ProfileUpdateResponseDto> updateProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ProfileUpdateRequestDto request) {
        return ResponseEntity.ok(profileService.updateProfile(userId, request));
    }

    // [PR-02] 프로필 조회
    @GetMapping("/{profileId}")
    public ResponseEntity<ProfileDetailResponseDto> getProfile(@PathVariable("profileId") Long profileId) {

        ProfileDetailResponseDto response = profileService.getProfile(profileId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

}
