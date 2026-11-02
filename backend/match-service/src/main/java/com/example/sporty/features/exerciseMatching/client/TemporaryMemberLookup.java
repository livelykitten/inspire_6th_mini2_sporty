package com.example.sporty.features.exerciseMatching.client;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * W-17 Feign 연동 전까지 사용하는 임시 구현이다.
 * 현재 단계에서는 모든 회원을 ACTIVE로 보고 프로필은 제공하지 않는다.
 */
@Component
public class TemporaryMemberLookup implements MemberLookup {

    @Override
    public void validateActive(Long userId) {
        // W-17에서 member-service 상태 조회로 교체한다.
    }

    @Override
    public Map<Long, MemberProfile> getProfiles(List<Long> userIds) {
        return Map.of();
    }
}
