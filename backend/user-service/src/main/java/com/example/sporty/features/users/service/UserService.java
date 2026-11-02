package com.example.sporty.features.users.service;

import com.example.sporty.features.commons.exception.profiles.ProfileNotFoundException;
import com.example.sporty.features.commons.exception.users.DuplicateEmailException;
import com.example.sporty.features.commons.exception.users.DuplicateNicknameException;
import com.example.sporty.features.auth.service.RefreshTokenService;
import com.example.sporty.features.exerciseMatching.service.MatchService;
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

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final SportPreferenceRepository sportPreferenceRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final MatchService matchService;

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
        UserEntity userEntity =
                UserSignUpRequestDto.toUserEntity(request, encodedPassword);

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
            SportPreferenceEntity preference =
                    SportPreferenceEntity.builder()
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
            UserEntity userEntity
                    = userRepository.findById(userId)
                    .orElseThrow(UserNotFoundException::new);

            // 2. profile 테이블에서 프로필 정보 조회
            ProfileEntity profileEntity
                    = profileRepository.findByUserId(userId)
                    .orElseThrow(ProfileNotFoundException::new);

            // 3. 프로필 ID로 회원 선호 종목들 얻기
            List<SportType> sportTypes =
                    sportPreferenceRepository.findByProfileId(profileEntity.getId())
                    .stream()
                    .map(SportPreferenceEntity::getSportType)
                    .toList();

            // 4. dto로 변환
            return UserInfoResponseDto.fromEntity(userEntity, profileEntity, sportTypes);
    }
    
    // [USR-04] 회원탈퇴
    public void withdrawal(String password) {

        // 1. 유저 ID 얻기
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long id = Long.parseLong(auth.getName());

        // 2. 유저 ID로 회원 조회
        UserEntity user = userRepository.findById(id)
                .orElseThrow(UserNotFoundException::new);

        // 3. 비밀번호 확인
        // 비밀번호 누락 검사
        if (password == null || password.isBlank()) {
            throw new PasswordMismatchException();
        }
        // 비밀번호 일치 여부 검사
        if (!passwordEncoder.matches(password,user.getPassword())) {
            throw new PasswordMismatchException();
        }

        // 같은 트랜잭션에서 생성 매치와 참가 기록을 먼저 정리한다.
        matchService.removeMatchesForWithdrawal(id);

        // 4. 탈퇴 처리(변경 감지) || 회원 상태를 탈퇴 상태로 변경, Refresh Token 삭제
        user.withdraw();
        refreshTokenService.delete(id);


    }


}
