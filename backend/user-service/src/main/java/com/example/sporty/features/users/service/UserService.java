package com.example.sporty.features.users.service;

import com.example.sporty.features.commons.exception.profiles.ProfileNotFoundException;
import com.example.sporty.features.commons.exception.users.DuplicateEmailException;
import com.example.sporty.features.commons.exception.users.DuplicateNicknameException;
import com.example.sporty.features.commons.exception.users.PasswordMismatchException;
import com.example.sporty.features.commons.exception.users.UserNotFoundException;
import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.profiles.domain.entity.ProfileEntity;
import com.example.sporty.features.profiles.repository.ProfileRepository;
import com.example.sporty.features.sportpreference.domain.entity.SportPreferenceEntity;
import com.example.sporty.features.sportpreference.repository.SportPreferenceRepository;
import com.example.sporty.features.users.domain.dto.UserInfoResponseDto;
import com.example.sporty.features.users.domain.dto.UserSignUpRequestDto;
import com.example.sporty.features.users.domain.entity.UserEntity;
import com.example.sporty.features.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import com.example.sporty.features.commons.exception.users.WithdrawalAlreadyPendingException;
import com.example.sporty.features.commons.exception.users.UserAlreadyWithdrawnException;
import com.example.sporty.features.users.domain.entity.UserStatus;
import com.example.sporty.features.users.domain.entity.UserOutbox;
import com.example.sporty.features.users.domain.dto.WithdrawalRequestedEvent;
import com.example.sporty.features.users.repository.UserOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final SportPreferenceRepository sportPreferenceRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserOutboxRepository userOutboxRepository;
    private final ObjectMapper objectMapper;

    // [USR-01] 회원가입
    public void signUp(UserSignUpRequestDto request) {

        // 1. 이메일, 닉네임 중복 여부 확인
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException();
        }

        if (profileRepository.existsByNickname(request.getNickname())) {
            throw new DuplicateNicknameException();
        }

        // 2. 회원 생성
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        // 비밀번호 해싱
        UserEntity userEntity = UserSignUpRequestDto.toUserEntity(request, encodedPassword);

        UserEntity savedUser = userRepository.save(userEntity);

        // 3. 프로필 생성
        ProfileEntity profileEntity = ProfileEntity.builder()
                .nickname(request.getNickname())
                .district(request.getDistrict())
                .user(savedUser)
                .build();
        ProfileEntity savedProfile = profileRepository.save(profileEntity);

        // 4. 선호 종목 생성
        for (SportType sportType : request.getSportTypes().stream().distinct().toList()) {
            SportPreferenceEntity preference = SportPreferenceEntity.builder()
                    .sportType(sportType)
                    .profile(savedProfile)
                    .build();

            sportPreferenceRepository.save(preference);
        }
    }

    // [USR-06] 회원 정보 조회
    @Transactional(readOnly = true)
    public UserInfoResponseDto getMyInfo(Long userId) {

        // 1. users 테이블에서 회원 정보 조회
        UserEntity userEntity = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (userEntity.getStatus() == UserStatus.WITHDRAWN) {
            throw new UserAlreadyWithdrawnException();
        }

        // 2. profile 테이블에서 프로필 정보 조회
        ProfileEntity profileEntity = profileRepository.findByUserId(userId)
                .orElseThrow(ProfileNotFoundException::new);

        // 3. 프로필 ID로 회원 선호 종목들 얻기
        List<SportType> sportTypes = sportPreferenceRepository.findByProfileId(profileEntity.getId())
                .stream()
                .map(SportPreferenceEntity::getSportType)
                .toList();

        // 4. dto로 변환
        return UserInfoResponseDto.fromEntity(userEntity, profileEntity, sportTypes);
    }

    // [USR-04] 회원탈퇴 접수
    public void withdrawal(String password) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = Long.parseLong(auth.getName());

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (password == null || password.isBlank()
                || !passwordEncoder.matches(password, user.getPassword())) {
            throw new PasswordMismatchException();
        }

        if (user.getStatus() == UserStatus.WITHDRAWAL_PENDING) {
            throw new WithdrawalAlreadyPendingException();
        }
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new UserAlreadyWithdrawnException();
        }

        String sagaId = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();

        int updated = userRepository.startWithdrawal(
                userId,
                sagaId,
                UserStatus.ACTIVE,
                UserStatus.WITHDRAWAL_PENDING);

        if (updated != 1) {
            UserEntity current = userRepository.findCurrentByIdForUpdate(userId)
                    .orElseThrow(UserNotFoundException::new);

            if (current.getStatus() == UserStatus.WITHDRAWN) {
                throw new UserAlreadyWithdrawnException();
            }
            if (current.getStatus() == UserStatus.WITHDRAWAL_PENDING) {
                throw new WithdrawalAlreadyPendingException();
            }

            throw new IllegalStateException("회원 탈퇴 접수 상태를 확인할 수 없습니다.");
        }

        String occurredAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));

        WithdrawalRequestedEvent event = new WithdrawalRequestedEvent(
                eventId,
                occurredAt,
                sagaId,
                userId);

        final String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "회원 탈퇴 이벤트 생성에 실패했습니다.",
                    exception);
        }

        userOutboxRepository.save(
                UserOutbox.withdrawalRequested(eventId, userId, payload));
    }

}
