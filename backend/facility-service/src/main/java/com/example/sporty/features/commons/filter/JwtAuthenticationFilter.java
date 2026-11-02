package com.example.sporty.features.commons.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.example.sporty.features.commons.config.PublicEndpoints;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    @Value("${jwt.secret}")
    private String secret;
    private Key key;

    @PostConstruct
    private void init() {
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || (PublicEndpoints.MATCHER.matches(request)
                        && !PublicEndpoints.MATCH_DETAIL.matches(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(header.substring(7))
                    .getBody();

            // 일반 API 인증에는 액세스 토큰만 허용
            if (!"ACCESS".equals(claims.get("tokenType", String.class))) {
                throw new JwtException("Access Token Required");
            }
            // 만료 시간이 없는 토큰 거절
            if (claims.getExpiration() == null) {
                throw new JwtException("JWT expiration is required");
            }

            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                throw new JwtException("JWT subject is required");
            }
            Long userId = Long.valueOf(subject);
            if (userId <= 0) {
                throw new JwtException("Invalid user ID");
            }

            String role = claims.get("role", String.class);
            var authentication = new UsernamePasswordAuthenticationToken(userId, null,
                    role != null ? List.of(new SimpleGrantedAuthority("ROLE_" + role)) : List.of());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            // 공개 상세 조회는 유효하지 않은 토큰을 비로그인 요청으로 처리한다.
            if (!PublicEndpoints.MATCH_DETAIL.matches(request)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }

        // Application failures must not be treated as invalid JWTs.
        filterChain.doFilter(request, response);
    }
}
