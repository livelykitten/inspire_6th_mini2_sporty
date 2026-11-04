package com.example.sporty.features.exerciseMatching.client;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.sporty.features.commons.exception.matches.MatchUserNotFoundException;
import com.example.sporty.features.commons.exception.matches.WithdrawnUserFoundException;
import com.example.sporty.features.exerciseMatching.domain.dto.UserProfileResponseDto;
import com.example.sporty.features.exerciseMatching.domain.dto.UserStatusResponseDto;

import feign.FeignException;
import lombok.RequiredArgsConstructor;

@Component 
@RequiredArgsConstructor 
public class MemberLookupImpl implements MemberLookup {

    private final UserServiceClient userServiceClient;

    @Override
    public void validateActive(Long userId) {

        try {
            UserStatusResponseDto userStatusResponseDto = userServiceClient.getUserStatus(userId);

            if (userStatusResponseDto == null || userStatusResponseDto.status() == null) {
                throw new IllegalStateException("회원 상태 응답 누락");
            }

            

            // if not active, send 401 USER_WITHDRAWN
            switch (userStatusResponseDto.status()) {
                case "ACTIVE" -> {
                    return;
                }
                case "WITHDRAWAL_PENDING", "WITHDRAWN" ->
                    throw new WithdrawnUserFoundException();
                default ->
                    throw new IllegalStateException("알 수 없는 회원 상태");
            }

        } catch (FeignException.NotFound e) {
            // IN-01의 404를 EM-01/06의 401 MATCH_USER_NOT_FOUND로 변환
            throw new MatchUserNotFoundException();
        }
    }

    @Override
    public Map<Long, MemberProfile> getProfiles(List<Long> userIds) {
        List<UserProfileResponseDto> userProfileResponseDtos = userServiceClient.getUserProfiles(userIds);

        return userProfileResponseDtos.stream()
            .collect(Collectors.toMap(
                UserProfileResponseDto::userId,
                UserProfileResponseDto::toMemberProfile
            ));



    }
    
}
