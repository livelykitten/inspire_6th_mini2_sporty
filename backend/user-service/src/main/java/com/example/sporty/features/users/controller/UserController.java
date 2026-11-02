package com.example.sporty.features.users.controller;

import java.util.Map;

import com.example.sporty.features.users.domain.dto.UserInfoResponseDto;
import com.example.sporty.features.users.domain.dto.UserSignUpRequestDto;
import com.example.sporty.features.users.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // [USR-01] 회원가입
    @PostMapping
    public ResponseEntity<Void> signUp(@RequestBody @Valid UserSignUpRequestDto request) {
        userService.signUp(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .build();
    }

    // [USR-06] 회원 정보 조회
    @GetMapping("/me")
    public ResponseEntity<UserInfoResponseDto> getMyInfo(@AuthenticationPrincipal Long userId) {

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(userService.getMyInfo(userId));
    }
    
    // [USR-04] 회원탈퇴
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdrawal( @RequestBody Map<String, String> request) {

        userService.withdrawal(request.get("password"));

        return ResponseEntity.noContent().build();
    }



}
