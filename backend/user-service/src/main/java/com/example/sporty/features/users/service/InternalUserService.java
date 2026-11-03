package com.example.sporty.features.users.service;

import com.example.sporty.features.commons.exception.users.UserNotFoundException;
import com.example.sporty.features.profiles.repository.ProfileRepository;
import com.example.sporty.features.users.domain.dto.UserProfileResponseDto;
import com.example.sporty.features.users.domain.dto.UserStatusResponseDto;
import com.example.sporty.features.users.domain.entity.UserStatus;
import com.example.sporty.features.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalUserService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    public UserStatusResponseDto getStatus(Long userId) {
        var user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        String sagaId = user.getStatus() == UserStatus.WITHDRAWAL_PENDING
                ? user.getWithdrawalSagaId()
                : null;

        return new UserStatusResponseDto(
                user.getId(),
                user.getStatus(),
                sagaId
        );
    }

    public List<UserProfileResponseDto> getProfiles(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }

        var users = userRepository.findAllById(userIds);
        var profiles = profileRepository.findAllByUser_IdIn(userIds);

        Set<Long> profileUserIds = new HashSet<>();

        for (var profile : profiles) {
            profileUserIds.add(profile.getUser().getId());
        }

        for (var user : users) {
            if (!profileUserIds.contains(user.getId())) {
                throw new IllegalStateException(
                        "회원의 프로필이 누락되었습니다."
                );
            }
        }

        return profiles.stream()
                .map(profile -> new UserProfileResponseDto(
                        profile.getUser().getId(),
                        profile.getId(),
                        profile.getNickname(),
                        profile.getImageUrl()
                ))
                .toList();
    }
}