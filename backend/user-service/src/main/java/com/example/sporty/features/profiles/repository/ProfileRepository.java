package com.example.sporty.features.profiles.repository;

import com.example.sporty.features.profiles.domain.entity.ProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProfileRepository extends JpaRepository<ProfileEntity, Long> {

    boolean existsByNickname(String nickname);

    // [PR-01] 해당 닉네임을 사용하는 다른 프로필이 있는지 검사
    boolean existsByNicknameAndIdNot(String nickname, Long id);

    Optional<ProfileEntity> findByUserId(Long userId);

    // 참가자별로 반복 조회하지 않고 해당 사용자들의 프로필을 한 번에 조회한다.
    List<ProfileEntity> findAllByUser_IdIn(List<Long> userIds);


}
