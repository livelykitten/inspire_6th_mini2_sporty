package com.example.sporty.features.ai.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.sporty.features.ai.agent.MatchAiAgent;
import com.example.sporty.features.ai.agent.MatchDraftAiAgent;
import com.example.sporty.features.ai.agent.MatchRecommendAiAgent;
import com.example.sporty.features.ai.domain.dto.AiRequestDto;
import com.example.sporty.features.commons.handler.ErrorResponse;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchResponseDto;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final MatchAiAgent matchAIAgent;
    private final MatchDraftAiAgent matchDraftAiAgent;
    private final MatchRecommendAiAgent matchRecommendAiAgent;

    // AI-02  body : { "prompt" : "이번 주말 강남에서 풋살 초보 매치 찾아줘" }
    @PostMapping("/matches/search")
    public ResponseEntity<?> searchMatches(@Valid @RequestBody AiRequestDto request) {
        System.out.println("debug >>>> ai controller searchMatches : " + request);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(matchAIAgent.search(request.getPrompt()));
    }

    // AI-01  body : { "prompt" : "토요일 저녁 7시 송파에서 농구 10명 모집" }
    // DB에 저장하지 않고 매치 개설 폼에 채울 초안만 반환한다.
    @PostMapping("/matches")
    public ResponseEntity<?> draftMatch(@Valid @RequestBody AiRequestDto request) {
        System.out.println("debug >>>> ai controller draftMatch : " + request);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(matchDraftAiAgent.draft(request.getPrompt()));
    }

    // 입력 검증 실패(@Valid) → 400 + 안내 문구
    // 이 컨트롤러에만 적용. 팀 공통 처리가 정해지면 GlobalExceptionHandler로 이동.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidInput(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("요청 내용을 확인해주세요.");

        System.out.println("debug >>>> ai controller invalid input : " + message);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder().message(message).build());
    }

    // AI-03  로그인 회원의 자치구·선호 종목·성별로 추천 매치 최대 3개 (후보가 없으면 빈 배열)
    @GetMapping("/matches/recommendations")
    public ResponseEntity<List<MatchResponseDto>> recommendMatches(@AuthenticationPrincipal Long userId) {
        System.out.println("debug >>>> ai controller recommendMatches : " + userId);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(matchRecommendAiAgent.recommend(userId));
    }
}