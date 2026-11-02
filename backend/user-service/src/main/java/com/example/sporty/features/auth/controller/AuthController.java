package com.example.sporty.features.auth.controller;

import com.example.sporty.features.auth.domain.dto.LoginRequestDto;
import com.example.sporty.features.auth.domain.dto.LoginResponseDto;
import com.example.sporty.features.auth.domain.dto.RefreshRequestDto;
import com.example.sporty.features.auth.domain.dto.RefreshResponseDto;
import com.example.sporty.features.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/auth")
@RequiredArgsConstructor
@RestController
public class AuthController {

    private final AuthService authService;

    // [USR-02] 로그인
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(
            @RequestBody @Valid LoginRequestDto request
    ) {
        LoginResponseDto response = authService.login(request);

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(response);
    }
    // [USR-03] 로그아웃
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
         authService.logout();

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // [USR-05] 토큰 재발급
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponseDto> refresh(@RequestBody(required = false) RefreshRequestDto request) {
        String refreshToken = request == null ? null : request.getRefreshToken();
        RefreshResponseDto response = authService.refresh(refreshToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(response);
    }

}
