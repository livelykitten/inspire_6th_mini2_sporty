package com.example.sporty.users;

import com.example.sporty.features.auth.service.AuthService;
import com.example.sporty.features.auth.service.RefreshTokenService;
import com.example.sporty.features.commons.exception.auth.InvalidRefreshTokenException;
import com.example.sporty.features.commons.exception.users.UserAlreadyWithdrawnException;
import com.example.sporty.features.commons.token.JwtProvider;
import com.example.sporty.features.profiles.domain.entity.ProfileEntity;
import com.example.sporty.features.profiles.repository.ProfileRepository;
import com.example.sporty.features.sportpreference.repository.SportPreferenceRepository;
import com.example.sporty.features.users.domain.entity.UserEntity;
import com.example.sporty.features.users.domain.entity.UserStatus;
import com.example.sporty.features.users.repository.UserOutboxRepository;
import com.example.sporty.features.users.repository.UserRepository;
import com.example.sporty.features.users.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WithdrawalAuthPolicyTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtProvider jwtProvider;

    @Mock
    RefreshTokenService refreshTokenService;

    @Mock
    ProfileRepository profileRepository;

    @Mock
    SportPreferenceRepository sportPreferenceRepository;

    @Mock
    UserOutboxRepository outboxRepository;

    @Test
    void pendingMemberCanRefreshToken() {
        var user = UserEntity.builder()
                .id(4L)
                .status(UserStatus.WITHDRAWAL_PENDING)
                .build();

        when(jwtProvider.validateRefreshToken("test-refresh")).thenReturn(4L);
        when(refreshTokenService.findByUserId(4L)).thenReturn("test-refresh");
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));
        when(jwtProvider.createAccessToken(4L)).thenReturn("new-access");

        authService().refresh("test-refresh");

        verify(jwtProvider).createAccessToken(4L);
        verify(refreshTokenService, never()).delete(anyLong());
    }

    @Test
    void withdrawnMemberCannotRefreshEvenIfRedisTokenRemains() {
        var user = UserEntity.builder()
                .id(4L)
                .status(UserStatus.WITHDRAWN)
                .build();

        when(jwtProvider.validateRefreshToken("test-refresh")).thenReturn(4L);
        when(refreshTokenService.findByUserId(4L)).thenReturn("test-refresh");
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService().refresh("test-refresh"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(jwtProvider, never()).createAccessToken(anyLong());
    }

    @Test
    void pendingMemberCanReadOwnInformation() {
        var user = UserEntity.builder()
                .id(4L)
                .status(UserStatus.WITHDRAWAL_PENDING)
                .build();

        var profile = ProfileEntity.builder()
                .id(14L)
                .nickname("pending-member")
                .user(user)
                .build();

        when(userRepository.findById(4L)).thenReturn(Optional.of(user));
        when(profileRepository.findByUserId(4L))
                .thenReturn(Optional.of(profile));
        when(sportPreferenceRepository.findByProfileId(14L))
                .thenReturn(List.of());

        assertThatCode(() -> userService().getMyInfo(4L))
                .doesNotThrowAnyException();

        verify(profileRepository).findByUserId(4L);
    }

    @Test
    void withdrawnMemberCannotReadOwnInformation() {
        var user = UserEntity.builder()
                .id(4L)
                .status(UserStatus.WITHDRAWN)
                .build();

        when(userRepository.findById(4L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService().getMyInfo(4L))
                .isInstanceOf(UserAlreadyWithdrawnException.class);

        verifyNoInteractions(profileRepository, sportPreferenceRepository);
    }

    private AuthService authService() {
        return new AuthService(
                userRepository,
                passwordEncoder,
                jwtProvider,
                refreshTokenService
        );
    }

    private UserService userService() {
        return new UserService(
                userRepository,
                profileRepository,
                sportPreferenceRepository,
                passwordEncoder,
                outboxRepository,
                new ObjectMapper()
        );
    }
}