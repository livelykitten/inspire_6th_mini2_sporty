package com.example.sporty.features.exerciseMatching.client;

import java.util.List;
import java.util.concurrent.TimeoutException;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.example.sporty.features.commons.exception.matches.UserServiceUnavailableException;
import com.example.sporty.features.exerciseMatching.domain.dto.UserProfileResponseDto;
import com.example.sporty.features.exerciseMatching.domain.dto.UserStatusResponseDto;

import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

@Component 
public class UserServiceFallbackFactory implements FallbackFactory<UserServiceClient> {

    @Override
    public UserServiceClient create(Throwable cause) {
        return new UserServiceClient() {
            
            @Override
            public UserStatusResponseDto getUserStatus(Long userId) {
                
                // 회원 미존재
                if (cause instanceof FeignException.NotFound e) {
                    throw e;
                }

                if (isUnavailable(cause)) {
                    throw new UserServiceUnavailableException(cause);
                }

                // 기타 조회 실패
                throw new IllegalStateException("회원 상태 조회 실패", cause);
            }

            @Override 
            public List<UserProfileResponseDto> getUserProfiles(List<Long> userIds) {
                if (isUnavailable(cause)) {
                    return List.of();
                }

                throw new IllegalStateException("회원 프로필 조회 실패", cause);
            }



        };
    }

    private boolean isUnavailable(Throwable cause) {
        if (cause instanceof CallNotPermittedException
            || cause instanceof RetryableException
            || cause instanceof TimeoutException
        )
            return true;

        if (cause instanceof FeignException e) {
            return e.status() == 502 || e.status() == 503 || e.status() == 504;
        }

        return false;
    }
    
}
