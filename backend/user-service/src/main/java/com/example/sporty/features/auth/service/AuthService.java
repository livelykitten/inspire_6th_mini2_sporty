package com.example.sporty.features.auth.service;

import com.example.sporty.features.auth.domain.dto.LoginRequestDto;
import com.example.sporty.features.auth.domain.dto.LoginResponseDto;
import com.example.sporty.features.auth.domain.dto.RefreshResponseDto;
import com.example.sporty.features.commons.exception.auth.InvalidRefreshTokenException;
import com.example.sporty.features.commons.exception.auth.LoginFailException;
import com.example.sporty.features.users.domain.entity.UserEntity;
import com.example.sporty.features.users.domain.entity.UserStatus;
import com.example.sporty.features.users.repository.UserRepository;
import com.example.sporty.features.commons.token.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    // [USR-02] 회원가입
    public LoginResponseDto login(LoginRequestDto request) {

        // 1. 이메일로 회원 조회
        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(LoginFailException::new);

        // 2. 정상 회원 여부 및 비밀번호 확인
        if (user.getStatus() != UserStatus.ACTIVE || !passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        )) {
            throw new LoginFailException();
        }

        // 3. JWT 생성
        String accessToken = jwtProvider.createAccessToken(
                user.getId()
        );
        String refreshToken = jwtProvider.createRefreshToken(user.getId());

        // 4. Redis 저장에 실패하면 로그인 성공 응답을 반환하지 않습니다.
        refreshTokenService.save(user.getId(), refreshToken,
                jwtProvider.getRefreshTokenExpirationSeconds());

        // 5. 토큰 반환
        return LoginResponseDto.builder()
                .userId(user.getId())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProvider.getAccessTokenExpirationSeconds())
                .build();
    }

    // [USR-03] 로그아웃
    public void logout() {
        // 스프링 시큐리티의 컨텍스트홀더에서 유저ID를 꺼내 Refresh Token 삭제
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long id = Long.parseLong(auth.getName());
        refreshTokenService.delete(id);
    }

    // [USR-05] 클라이언트가 보낸 RT와 Redis의 최신 RT가 일치하면 AT 발급
    public RefreshResponseDto refresh(String refreshToken) {
        // 1. RT 검증 후 회원 ID 얻기
        Long userId = jwtProvider.validateRefreshToken(refreshToken);

        // 2. Redis에 저장된 RT와 비교
        String savedToken = refreshTokenService.findByUserId(userId);
        if (!refreshToken.equals(savedToken)) {
            throw new InvalidRefreshTokenException();
        }

        // 3. 정상 회원 여부 확인
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(InvalidRefreshTokenException::new);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidRefreshTokenException();
        }

        // 4. AT만 재발급하여 반환 (기존 RT와 만료 시간은 유지)
        String accessToken = jwtProvider.createAccessToken(userId);
        return RefreshResponseDto.builder()
                .accessToken(accessToken)
                .build();
    }
}
