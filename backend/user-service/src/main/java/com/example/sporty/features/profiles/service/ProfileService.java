package com.example.sporty.features.profiles.service;

import com.example.sporty.features.commons.exception.profiles.ProfileNotFoundException;
import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.profiles.domain.dto.ProfileDetailResponseDto;
import com.example.sporty.features.profiles.domain.dto.ProfileUpdateRequestDto;
import com.example.sporty.features.profiles.domain.dto.ProfileUpdateResponseDto;
import com.example.sporty.features.commons.exception.users.DuplicateNicknameException;
import com.example.sporty.features.profiles.domain.entity.ProfileEntity;
import com.example.sporty.features.profiles.repository.ProfileRepository;
import com.example.sporty.features.sportpreference.domain.entity.SportPreferenceEntity;
import com.example.sporty.features.sportpreference.repository.SportPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final SportPreferenceRepository sportPreferenceRepository;

    // [PR-01] 프로필 수정
    @Transactional
    public ProfileUpdateResponseDto updateProfile(Long userId, ProfileUpdateRequestDto request) {

        // 1. 프로필 엔티티 얻기
        ProfileEntity profile = profileRepository.findByUserId(userId)
                .orElseThrow(ProfileNotFoundException::new);

        // 2. 해당 닉네임을 사용하는 다른 프로필이 있는지 검사
        String nickname = request.getNickname().trim();

        if (profileRepository.existsByNicknameAndIdNot(nickname, profile.getId())) {
            throw new DuplicateNicknameException();
        }

        // 3. 변경 감지로 닉네임 저장
        profile.updateProfile(nickname, request.getDistrict());

        // 4. 기존 선호 종목 삭제
        sportPreferenceRepository.deleteByProfileId(profile.getId());
        sportPreferenceRepository.flush();

        // 5. 새로운 선호 종목 등록
        // 선호 종목 중복 제거
        List<SportType> sportTypes = request.getSportTypes()
                .stream()
                .distinct()
                .toList();

        List<SportPreferenceEntity> preferences = sportTypes.stream()
                .map(sportType -> SportPreferenceEntity.builder()
                        .profile(profile)
                        .sportType(sportType)
                        .build())
                .toList();
        sportPreferenceRepository.saveAll(preferences);

        return ProfileUpdateResponseDto.fromEntity(profile, sportTypes);
    }

    // [PR-02] 프로필 조회
    @Transactional(readOnly = true)
    public ProfileDetailResponseDto getProfile(Long profileId) {

        // 1. 프로필 ID로 프로필 엔티티 얻기
        ProfileEntity profile
                = profileRepository.findById(profileId)
                .orElseThrow(() -> new ProfileNotFoundException());

        // 2. 프로필 ID로 회원 선호 종목들 얻기
        List<SportType> sportTypes =
                sportPreferenceRepository.findByProfileId(profileId)
                        .stream()
                        .map(SportPreferenceEntity::getSportType)
                        .toList();

        return ProfileDetailResponseDto.fromEntity(profile, sportTypes);
    }

}
