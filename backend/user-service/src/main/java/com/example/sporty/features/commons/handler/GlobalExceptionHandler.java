package com.example.sporty.features.commons.handler;

import com.example.sporty.features.commons.exception.matches.MatchUserNotFoundException;
import com.example.sporty.features.commons.exception.matches.WithdrawnUserFoundException;
import com.example.sporty.features.commons.exception.matches.ServiceNotFoundException;
import com.example.sporty.features.commons.exception.auth.LoginFailException;
import com.example.sporty.features.commons.exception.auth.InvalidRefreshTokenException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchDeleteForbiddenException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchModifyForbiddenException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchNotFoundException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchOwnerCannotLeaveException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchParticipantNotFoundException;
import com.example.sporty.features.commons.exception.profiles.ProfileNotFoundException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchAlreadyJoinedException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchAlreadyStartedException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchFullException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchRecruitmentClosedException;
import com.example.sporty.features.commons.exception.users.DuplicateEmailException;
import com.example.sporty.features.commons.exception.users.DuplicateNicknameException;
import com.example.sporty.features.commons.exception.users.PasswordMismatchException;
import com.example.sporty.features.commons.exception.users.UserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.sporty.features.commons.exception.ai.AiDraftException;
import com.example.sporty.features.commons.exception.ai.AiRecommendException;
import com.example.sporty.features.commons.exception.ai.AiSearchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MatchParticipantNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMatchParticipantNotFound(MatchParticipantNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.builder()
                        .code("MATCH_PARTICIPANT_NOT_FOUND")
                        .message(e.getMessage())
                        .build());
    }

    @ExceptionHandler(MatchOwnerCannotLeaveException.class)
    public ResponseEntity<ErrorResponse> handleMatchOwnerCannotLeave(MatchOwnerCannotLeaveException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.builder()
                        .code("MATCH_OWNER_CANNOT_LEAVE")
                        .message(e.getMessage())
                        .build());
    }


    @ExceptionHandler(MatchModifyForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleMatchModifyForbiddenException(MatchModifyForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.builder()
                        .code("MATCH_MODIFY_FORBIDDEN")
                        .message(e.getMessage())
                        .build());
    }

    @ExceptionHandler(MatchAlreadyJoinedException.class)
    public ResponseEntity<ErrorResponse> handleMatchAlreadyJoined(MatchAlreadyJoinedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.builder()
                        .code("MATCH_ALREADY_JOINED")
                        .message(e.getMessage())
                        .build());
    }

    @ExceptionHandler(MatchRecruitmentClosedException.class)
    public ResponseEntity<ErrorResponse> handleMatchRecruitmentClosed(MatchRecruitmentClosedException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .code("MATCH_RECRUITMENT_CLOSED")
                        .message(e.getMessage())
                        .build());
    }

    @ExceptionHandler(MatchAlreadyStartedException.class)
    public ResponseEntity<ErrorResponse> handleMatchAlreadyStarted(MatchAlreadyStartedException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .code("MATCH_ALREADY_STARTED")
                        .message(e.getMessage())
                        .build());
    }

    @ExceptionHandler(MatchFullException.class)
    public ResponseEntity<ErrorResponse> handleMatchFull(MatchFullException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .code("MATCH_FULL")
                        .message(e.getMessage())
                        .build());
    }
    
    @ExceptionHandler(MatchDeleteForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleMatchDeleteForbidden(MatchDeleteForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.builder()
                        .code("MATCH_DELETE_FORBIDDEN")
                        .message(e.getMessage())
                        .build());

    }
    @ExceptionHandler(ServiceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleServiceNotFound(ServiceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.builder().code("SERVICE_NOT_FOUND").message(e.getMessage()).build());
    }

    @ExceptionHandler(MatchNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMatchNotFound(MatchNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.builder()
                        .code("MATCH_NOT_FOUND")
                        .message(e.getMessage())
                        .build());
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(
            DuplicateEmailException e
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.builder()
                        .code("DUPLICATE_EMAIL")
                        .message(e.getMessage())
                        .build());
    }

    @ExceptionHandler(DuplicateNicknameException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateNickname(
            DuplicateNicknameException e
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.builder()
                        .code("DUPLICATE_NICKNAME")
                        .message(e.getMessage())
                        .build());
    }

    @ExceptionHandler (MatchUserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMatchUserNotFoundException(
        MatchUserNotFoundException e
    ) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.builder()
                        .code("MATCH_USER_NOT_FOUND")
                        .message(e.getMessage())
                        .build());
    }

    @ExceptionHandler (WithdrawnUserFoundException.class)
    public ResponseEntity<ErrorResponse> handleWithdrawnUserFoundException(
        WithdrawnUserFoundException e
    ) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.builder()
                        .code("USER_WITHDRAWN")
                        .message(e.getMessage())
                        .build());
    }
                
    @ExceptionHandler(LoginFailException.class)
    public ResponseEntity<ErrorResponse> handleLoginFail(
            LoginFailException e
    ) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.builder()
                        .code("LOGIN_FAILED")
                        .message(e.getMessage())
                        .build());
    }


    @ExceptionHandler(ProfileNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProfileNotFound (
            ProfileNotFoundException e
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.builder()
                        .code("PROFILE_NOT_FOUND")
                        .message(e.getMessage())
                        .build());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            UserNotFoundException e
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.builder()
                        .code("USER_NOT_FOUND")
                        .message(e.getMessage())
                        .build());
    }

    @ExceptionHandler(PasswordMismatchException.class)
    public ResponseEntity<ErrorResponse> handlePasswordMismatch(
            PasswordMismatchException e
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .code("PASSWORD_MISMATCH")
                        .message(e.getMessage())
                        .build());
    }

    // AI 검색 조건 없음 → 400
    @ExceptionHandler(AiSearchException.class)
    public ResponseEntity<?> handlerAiSearch(AiSearchException e) {
        System.out.println("debug >>>> GlobalExceptionHandler handlerAiSearch");
        System.out.println("debug >>>> e.message " + e.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .message(e.getMessage())
                .build();

        // code : BAD_REQUEST(400)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // AI 매치 초안 정보 없음 → 400
    @ExceptionHandler(AiDraftException.class)
    public ResponseEntity<?> handlerAiDraft(AiDraftException e) {
        System.out.println("debug >>>> GlobalExceptionHandler handlerAiDraft");
        System.out.println("debug >>>> e.message " + e.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .message(e.getMessage())
                .build();

        // code : BAD_REQUEST(400)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // AI 추천 결과를 만들지 못함 → 500
    @ExceptionHandler(AiRecommendException.class)
    public ResponseEntity<?> handlerAiRecommend(AiRecommendException e) {
        System.out.println("debug >>>> GlobalExceptionHandler handlerAiRecommend");
        System.out.println("debug >>>> e.message " + e.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .message(e.getMessage())
                .build();

        // code : INTERNAL_SERVER_ERROR(500)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handlerInvalidRefreshToken (
            InvalidRefreshTokenException e
    ) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.builder()
                        .code("INVALID_REFRESH_TOKEN")
                        .message(e.getMessage())
                        .build());
    }

}
