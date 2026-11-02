package com.example.sporty.features.exerciseMatching.controller;

import org.springframework.web.bind.annotation.RestController;

import com.example.sporty.features.exerciseMatching.domain.dto.MatchCreateRequestDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchSearchRequestDto;
import com.example.sporty.features.exerciseMatching.service.MatchService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import com.example.sporty.features.exerciseMatching.domain.dto.MyMatchResponseDto;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;


import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.sporty.features.exerciseMatching.domain.dto.MatchDetailResponseDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchModifyRequestDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchParticipantResponseDto;
import org.springframework.web.bind.annotation.PutMapping;


@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    // [EM-08] 내 매치 목록 조회
    @GetMapping("/me")
    public ResponseEntity<List<MyMatchResponseDto>> getMyMatches(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(matchService.getMyMatches(userId));
    }

    @PostMapping
    public ResponseEntity<?> createMatch(
        @Valid @RequestBody MatchCreateRequestDto req,
        BindingResult bindingResult
    ) {
        System.out.println("debug >> MatchController.createMatch() called with: " + req);
        
        // check for validation errors
        if (bindingResult.hasErrors()) {
            Map<String, String> errMap = new HashMap<>();

            bindingResult.getAllErrors().forEach(e -> {
                FieldError field = (FieldError)e; 
                String msg = e.getDefaultMessage();
                errMap.put(field.getField(), msg);
            });
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errMap);
        }

        // proceed if there are no errors
        
        return ResponseEntity.status(HttpStatus.CREATED).body(matchService.createMatch(req));
    }

    @GetMapping
    public ResponseEntity<?> search(
        @Valid @ModelAttribute MatchSearchRequestDto req,
        BindingResult bindingResult
    ) {
        System.out.println("debug >> MatchController.getList() called with: " + req);

        if (bindingResult.hasErrors()) {
            Map<String, String> errMap = new HashMap<>();
            bindingResult.getAllErrors().forEach(error -> {
                String key = error instanceof FieldError fieldError
                    ? fieldError.getField() : error.getObjectName();
                errMap.put(key, error.getDefaultMessage());
            });
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errMap);
        }
        
        return ResponseEntity
            .status(HttpStatus.OK)
            .body(matchService.searchMatches(req));
    }
    
    
    
    // EM-03: 비로그인 조회도 허용하며, 로그인한 경우 내 생성자/참여 여부를 함께 반환한다.
    @GetMapping("/{matchId}")
    public ResponseEntity<MatchDetailResponseDto> getMatchDetail(
            @PathVariable("matchId") Long matchId,
            @AuthenticationPrincipal Long userId) {

        return ResponseEntity.ok(matchService.getMatchDetail(matchId, userId));
    }

    // EM-04: 운동 매칭 기능 수정. 생성자 전용. 인증/권한/매치 확인 실패 시 401/403/404.
    @PutMapping("/{matchId}")
    public ResponseEntity<?> modifyMatch(
            @PathVariable("matchId") Long matchId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody MatchModifyRequestDto req,
            BindingResult bindingResult
        ) {
        System.out.println("debug >> MatchController.modifyMatch() called with: " + req);
        
        if (bindingResult.hasErrors()) {
            Map<String, String> errMap = new HashMap<>();
            bindingResult.getAllErrors().forEach(error -> {
                String key = error instanceof FieldError fieldError
                    ? fieldError.getField() : error.getObjectName();
                errMap.put(key, error.getDefaultMessage());
            });
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errMap);
        }

        return ResponseEntity.ok(matchService.modifyMatch(matchId, userId, req));
    }

    // EM-05: 생성자 전용 삭제. 인증/권한/매치 확인 실패 시 401/403/404.
    @DeleteMapping("/{matchId}")
    public ResponseEntity<Void> deleteMatch(
            @PathVariable("matchId") Long matchId,
            @AuthenticationPrincipal Long userId) {
        matchService.deleteMatch(matchId, userId);
        return ResponseEntity.noContent().build();
    }

    // EM-06: 로그인 사용자를 일반 참가자로 등록하고 201을 반환한다.
    @PostMapping("/{matchId}/participants")
    public ResponseEntity<MatchParticipantResponseDto> joinMatch(
            @PathVariable("matchId") Long matchId,
            @AuthenticationPrincipal Long userId) {

        return ResponseEntity.status(HttpStatus.CREATED).body(matchService.joinMatch(matchId, userId));
    }

    // EM-07: 일반 참가자 탈퇴. 성공 204, 인증/OWNER/매치·참가 정보 오류는 401/403/404.
    @DeleteMapping("/{matchId}/participants/me")
    public ResponseEntity<Void> leaveMatch(
            @PathVariable("matchId") Long matchId,
            @AuthenticationPrincipal Long userId) {
        matchService.leaveMatch(matchId, userId);
        return ResponseEntity.noContent().build();
    }
}
