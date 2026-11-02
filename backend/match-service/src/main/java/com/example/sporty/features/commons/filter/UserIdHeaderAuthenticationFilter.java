package com.example.sporty.features.commons.filter;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class UserIdHeaderAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader("X-User-Id");

        if (header != null && header.matches("[0-9]+")) {
            try {
                long userId = Long.parseLong(header);

                if (userId > 0) {
                    var context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    userId, null, List.of()
                            )
                    );
                    SecurityContextHolder.setContext(context);
                }
            } catch (NumberFormatException ignored) {
                // Long 범위를 넘는 값은 인증 정보로 사용하지 않는다.
            }
        }

        filterChain.doFilter(request, response);
    }
}
