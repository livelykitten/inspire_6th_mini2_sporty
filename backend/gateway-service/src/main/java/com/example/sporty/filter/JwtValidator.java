package com.example.sporty.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 1차 JwtAuthenticationFilter 52-75행의 검증 규칙을 그대로 옮김. */
@Component
public class JwtValidator {
    private final Key key;

    public JwtValidator(@Value("${jwt.secret}") String secret) {
        // user-service(JwtProvider)와 같은 방식: 시크릿 문자열의 UTF-8 바이트 (Base64 디코딩 아님)
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 유효한 액세스 토큰이면 회원 ID, 아니면 empty. */
    public Optional<Long> validate(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            // 리프레시 토큰도 같은 키로 서명되므로 액세스 토큰만 허용
            if (!"ACCESS".equals(claims.get("tokenType", String.class))) {
                return Optional.empty();
            }
            if (claims.getExpiration() == null) {
                return Optional.empty();
            }
            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                return Optional.empty();
            }
            Long userId = Long.valueOf(subject);
            return userId > 0 ? Optional.of(userId) : Optional.empty();
        } catch (JwtException | IllegalArgumentException exception) {
            // Long 범위를 넘는 sub 의 NumberFormatException 도 여기서 잡아 401 (안 잡으면 500)
            return Optional.empty();
        }
    }
}
