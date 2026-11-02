package com.example.sporty.features.exerciseMatching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.sporty.features.commons.exception.exerciseMatching.MatchNotFoundException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchOwnerCannotLeaveException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchParticipantNotFoundException;
import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchDetailResponseDto;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchEntity;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchParticipantEntity;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchParticipantRole;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;
import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.features.exerciseMatching.repository.MatchParticipantRepository;
import com.example.sporty.features.exerciseMatching.repository.MatchRepository;
import com.example.sporty.features.profiles.domain.entity.ProfileEntity;
import com.example.sporty.features.profiles.repository.ProfileRepository;
import com.example.sporty.features.users.domain.entity.UserEntity;
import com.example.sporty.features.users.repository.UserRepository;
import com.example.sporty.features.facilities.repository.ServiceRepository;
import com.example.sporty.support.FacilityFixtures;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private MatchParticipantRepository matchParticipantRepository;

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ServiceRepository serviceRepository;

    @InjectMocks
    private MatchService matchService;

    @Test
    @DisplayName("[TC-EM03-01] 매칭 상세 정상 조회 - 기본 정보와 조회된 참가 인원을 반환한다")
    void getMatchDetailReturnsStoredMatchInformation() {
        LocalDateTime startAt = LocalDateTime.of(2026, 9, 26, 19, 0);
        LocalDateTime endAt = startAt.plusHours(2);
        MatchEntity match = MatchEntity.builder()
                .id(101L)
                .title("주말 풋살 모집")
                .description("같이 풋살하실 분")
                .startAt(startAt)
                .endAt(endAt)
                .maxParticipant(10)
                .status(MatchStatus.CLOSED)
                .skillLevel(SkillLevel.INTERMEDIATE)
                .sportType(SportType.FUTSAL)
                .genderGroup(GenderGroup.FEMALE)
                .service(FacilityFixtures.service(7L, true))
                .build();
        when(matchRepository.findById(101L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findAllByMatch_IdOrderByIdAsc(101L)).thenReturn(List.of(
                participant(1L, MatchParticipantRole.OWNER),
                participant(2L, MatchParticipantRole.PARTICIPANT)));
        // 프로필 조회 순서가 달라도 사용자 ID로 연결해야 한다.
        when(profileRepository.findAllByUser_IdIn(List.of(1L, 2L))).thenReturn(List.of(
                profile(2L, 402L, "참가자", null),
                profile(1L, 401L, "생성자", "https://example.com/owner.png")));

        MatchDetailResponseDto response = matchService.getMatchDetail(101L, null);

        assertThat(response.getMatchId()).isEqualTo(101L);
        assertThat(response.getTitle()).isEqualTo("주말 풋살 모집");
        assertThat(response.getDescription()).isEqualTo("같이 풋살하실 분");
        assertThat(response.getStartAt()).isEqualTo(startAt);
        assertThat(response.getEndAt()).isEqualTo(endAt);
        assertThat(response.getMaxParticipant()).isEqualTo(10);
        assertThat(response.getCurrentParticipantCount()).isEqualTo(2);
        assertThat(response.getStatus()).isEqualTo(MatchStatus.CLOSED);
        assertThat(response.getSkillLevel()).isEqualTo(SkillLevel.INTERMEDIATE);
        assertThat(response.getSportType()).isEqualTo(SportType.FUTSAL);
        assertThat(response.getGenderGroup()).isEqualTo(GenderGroup.FEMALE);
        assertThat(response.getServiceId()).isEqualTo(7L);

        assertThat(response.getParticipants()).hasSize(2);
        assertThat(response.getParticipants().get(0).getProfileId()).isEqualTo(401L);
        assertThat(response.getParticipants().get(0).getNickname()).isEqualTo("생성자");
        assertThat(response.getParticipants().get(0).getImageUrl()).isEqualTo("https://example.com/owner.png");
        assertThat(response.getParticipants().get(0).getRole()).isEqualTo(MatchParticipantRole.OWNER);
        assertThat(response.getParticipants().get(1).getProfileId()).isEqualTo(402L);
        assertThat(response.getParticipants().get(1).getNickname()).isEqualTo("참가자");
        assertThat(response.getParticipants().get(1).getImageUrl()).isNull();
        assertThat(response.getParticipants().get(1).getRole()).isEqualTo(MatchParticipantRole.PARTICIPANT);
        assertThat(response.getIsOwner()).isFalse();
        assertThat(response.getIsParticipant()).isFalse();
        // 연결된 서비스와 장소 정보를 반환한다.
        assertThat(response.getServiceName()).isEqualTo("Test service 7");
        assertThat(response.getLocationName()).isEqualTo("Test location");
        assertThat(response.getRegion()).isEqualTo("강남구");
        verify(profileRepository).findAllByUser_IdIn(List.of(1L, 2L));
    }

    @Test
    @DisplayName("[추가 검증] 참가 정보가 없으면 현재 인원은 0이며 생성자 수를 임의로 더하지 않는다")
    void getMatchDetailReturnsZeroWhenNoParticipantsAreStored() {
        MatchEntity match = MatchEntity.builder().id(101L).service(FacilityFixtures.service(7L, true)).build();
        when(matchRepository.findById(101L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findAllByMatch_IdOrderByIdAsc(101L)).thenReturn(List.of());

        MatchDetailResponseDto response = matchService.getMatchDetail(101L, 1L);

        assertThat(response.getCurrentParticipantCount()).isZero();
        assertThat(response.getParticipants()).isEmpty();
        assertThat(response.getIsOwner()).isFalse();
        assertThat(response.getIsParticipant()).isFalse();
        verifyNoInteractions(profileRepository);
    }

    @Test
    @DisplayName("[TC-EM03-02] 존재하지 않는 매칭 상세 조회 - 404 예외가 발생한다")
    void getMatchDetailThrowsNotFoundWhenMatchDoesNotExist() {
        when(matchRepository.findById(999999L)).thenReturn(Optional.empty());

        MatchNotFoundException exception = assertThrows(MatchNotFoundException.class,
                () -> matchService.getMatchDetail(999999L, null));

        assertThat(exception.getMessage()).isEqualTo("매치를 찾을 수 없습니다.");
        verifyNoInteractions(matchParticipantRepository, profileRepository);
    }

    @ParameterizedTest
    @CsvSource({"1,true,true", "2,false,true", "3,false,false"})
    @DisplayName("조회 사용자의 해당 매치 참가 역할로 생성자와 참여 여부를 구분한다")
    void getMatchDetailReturnsCurrentUserState(Long userId, boolean owner, boolean joined) {
        when(matchRepository.findById(101L)).thenReturn(Optional.of(MatchEntity.builder().id(101L).service(FacilityFixtures.service(7L, true)).build()));
        when(matchParticipantRepository.findAllByMatch_IdOrderByIdAsc(101L)).thenReturn(List.of(
                participant(1L, MatchParticipantRole.OWNER),
                participant(2L, MatchParticipantRole.PARTICIPANT)));
        when(profileRepository.findAllByUser_IdIn(List.of(1L, 2L))).thenReturn(List.of());

        MatchDetailResponseDto response = matchService.getMatchDetail(101L, userId);

        assertThat(response.getIsOwner()).isEqualTo(owner);
        assertThat(response.getIsParticipant()).isEqualTo(joined);
    }

    @Test
    @DisplayName("프로필이 없어도 참가자 수와 역할을 유지하고 개인정보 필드는 null로 반환한다")
    void missingProfileDoesNotRemoveParticipant() {
        when(matchRepository.findById(101L)).thenReturn(Optional.of(MatchEntity.builder().id(101L).service(FacilityFixtures.service(7L, true)).build()));
        when(matchParticipantRepository.findAllByMatch_IdOrderByIdAsc(101L))
                .thenReturn(List.of(participant(1L, MatchParticipantRole.OWNER)));
        when(profileRepository.findAllByUser_IdIn(List.of(1L))).thenReturn(List.of());

        MatchDetailResponseDto response = matchService.getMatchDetail(101L, 1L);

        assertThat(response.getCurrentParticipantCount()).isEqualTo(1);
        assertThat(response.getParticipants()).hasSize(1);
        assertThat(response.getParticipants().get(0).getProfileId()).isNull();
        assertThat(response.getParticipants().get(0).getNickname()).isNull();
        assertThat(response.getParticipants().get(0).getImageUrl()).isNull();
        assertThat(response.getParticipants().get(0).getRole()).isEqualTo(MatchParticipantRole.OWNER);
        assertThat(response.getIsOwner()).isTrue();
        assertThat(response.getIsParticipant()).isTrue();
    }

    @Test
    @DisplayName("프로필 ID는 Long 값을 잘라내지 않고 반환한다")
    void profileIdRetainsLongValue() {
        when(matchRepository.findById(101L)).thenReturn(Optional.of(MatchEntity.builder().id(101L).service(FacilityFixtures.service(7L, true)).build()));
        when(matchParticipantRepository.findAllByMatch_IdOrderByIdAsc(101L))
                .thenReturn(List.of(participant(1L, MatchParticipantRole.OWNER)));
        when(profileRepository.findAllByUser_IdIn(List.of(1L)))
                .thenReturn(List.of(profile(1L, 2147483648L, "생성자", null)));

        assertThat(matchService.getMatchDetail(101L, null).getParticipants().get(0).getProfileId())
                .isEqualTo(2147483648L);
    }

    @ParameterizedTest
    @CsvSource({"1,RECRUITING", "-1,CLOSED"})
    @DisplayName("[EM07-001, EM07-002] 시작 전후 모두 본인의 참가 정보만 삭제하고 매치는 유지한다")
    void leaveMatchDeletesOnlyCurrentParticipant(int startOffsetHours, MatchStatus status) {
        MatchEntity match = MatchEntity.builder().id(301L)
                .startAt(LocalDateTime.now().plusHours(startOffsetHours))
                .status(status).build();
        MatchParticipantEntity participant = MatchParticipantEntity.builder()
                .id(10L).match(match).user(UserEntity.builder().id(2L).build()).role(MatchParticipantRole.PARTICIPANT).build();
        when(matchRepository.findByIdForUpdate(301L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatch_IdAndUserId(301L, 2L))
                .thenReturn(Optional.of(participant));

        matchService.leaveMatch(301L, 2L);

        verify(matchRepository).findByIdForUpdate(301L);
        verify(matchParticipantRepository).findByMatch_IdAndUserId(301L, 2L);
        verify(matchParticipantRepository).delete(participant);
        verifyNoMoreInteractions(matchRepository, matchParticipantRepository);
        verifyNoInteractions(profileRepository);
        assertThat(match.getStatus()).isEqualTo(status);
    }

    @Test
    @DisplayName("[EM07-003] OWNER 탈퇴 시 참가 정보와 매치를 삭제하지 않는다")
    void leaveMatchRejectsOwner() {
        when(matchRepository.findByIdForUpdate(303L)).thenReturn(Optional.of(MatchEntity.builder().id(303L).build()));
        when(matchParticipantRepository.findByMatch_IdAndUserId(303L, 1L))
                .thenReturn(Optional.of(participant(1L, MatchParticipantRole.OWNER)));

        assertThrows(MatchOwnerCannotLeaveException.class, () -> matchService.leaveMatch(303L, 1L));

        verify(matchRepository).findByIdForUpdate(303L);
        verify(matchParticipantRepository).findByMatch_IdAndUserId(303L, 1L);
        verifyNoMoreInteractions(matchRepository, matchParticipantRepository);
        verifyNoInteractions(profileRepository);
    }

    @Test
    @DisplayName("[EM07-004] 미참여자 탈퇴 시 기존 참가 정보와 매치를 변경하지 않는다")
    void leaveMatchRejectsNonParticipant() {
        when(matchRepository.findByIdForUpdate(304L)).thenReturn(Optional.of(MatchEntity.builder().id(304L).build()));
        when(matchParticipantRepository.findByMatch_IdAndUserId(304L, 5L)).thenReturn(Optional.empty());

        assertThrows(MatchParticipantNotFoundException.class, () -> matchService.leaveMatch(304L, 5L));

        verify(matchRepository).findByIdForUpdate(304L);
        verify(matchParticipantRepository).findByMatch_IdAndUserId(304L, 5L);
        verifyNoMoreInteractions(matchRepository, matchParticipantRepository);
        verifyNoInteractions(profileRepository);
    }

    @Test
    @DisplayName("[EM07-005] 없는 매치 탈퇴 시 참가 정보에 접근하지 않는다")
    void leaveMatchRejectsMissingMatch() {
        when(matchRepository.findByIdForUpdate(99999L)).thenReturn(Optional.empty());

        assertThrows(MatchNotFoundException.class, () -> matchService.leaveMatch(99999L, 2L));

        verify(matchRepository).findByIdForUpdate(99999L);
        verifyNoMoreInteractions(matchRepository);
        verifyNoInteractions(matchParticipantRepository, profileRepository);
    }

    @ParameterizedTest
    @CsvSource({"1,4,RECRUITING", "0,4,CLOSED", "-1,4,CLOSED", "1,5,CLOSED", "1,6,CLOSED"})
    @DisplayName("마감된 매치는 시작 전이고 탈퇴 후 빈자리가 있을 때만 모집을 재개한다")
    void leaveReopensOnlyBeforeStartWithVacancy(int startOffsetSeconds, long remainingCount,
            MatchStatus expectedStatus) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 28, 19, 0);
        MatchEntity match = MatchEntity.builder().id(301L).maxParticipant(5)
                .startAt(now.plusSeconds(startOffsetSeconds)).status(MatchStatus.CLOSED).build();
        when(matchRepository.findByIdForUpdate(301L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatch_IdAndUserId(301L, 2L))
                .thenReturn(Optional.of(participant(2L, MatchParticipantRole.PARTICIPANT)));
        if (startOffsetSeconds > 0) {
            when(matchParticipantRepository.countByMatch_Id(301L)).thenReturn(remainingCount);
        }

        try (MockedStatic<LocalDateTime> clock = mockStatic(LocalDateTime.class)) {
            clock.when(LocalDateTime::now).thenReturn(now);
            matchService.leaveMatch(301L, 2L);
        }

        assertThat(match.getStatus()).isEqualTo(expectedStatus);
        if (startOffsetSeconds <= 0) {
            verify(matchParticipantRepository, never()).countByMatch_Id(301L);
        }
    }

    private MatchParticipantEntity participant(Long userId, MatchParticipantRole role) {
        return MatchParticipantEntity.builder()
                .user(UserEntity.builder().id(userId).build())
                .role(role)
                .build();
    }

    private ProfileEntity profile(Long userId, Long profileId, String nickname, String imageUrl) {
        return ProfileEntity.builder()
                .id(profileId)
                .user(UserEntity.builder().id(userId).build())
                .nickname(nickname)
                .imageUrl(imageUrl)
                .build();
    }
}
