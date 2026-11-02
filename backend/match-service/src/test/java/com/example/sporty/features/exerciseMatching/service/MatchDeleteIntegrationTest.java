package com.example.sporty.features.exerciseMatching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import jakarta.persistence.EntityManager;
import com.example.sporty.features.users.domain.entity.UserEntity;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.support.MatchUserFixtures;
import com.example.sporty.support.FacilityFixtures;
import com.example.sporty.features.facilities.domain.entity.ServiceEntity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.sporty.features.commons.exception.exerciseMatching.MatchDeleteForbiddenException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchNotFoundException;
import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchEntity;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchParticipantEntity;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchParticipantRole;
import com.example.sporty.features.exerciseMatching.repository.MatchParticipantRepository;
import com.example.sporty.features.exerciseMatching.repository.MatchRepository;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.auto_quote_keyword=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(MatchService.class)
// 테스트의 바깥 트랜잭션 없이 실제 Service의 커밋/롤백 결과를 조회한다.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MatchDeleteIntegrationTest {

    @Autowired
    private MatchService matchService;

    @MockitoSpyBean
    private MatchRepository matchRepository;

    @Autowired
    private MatchParticipantRepository matchParticipantRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    private Map<Long, UserEntity> users;
    private Long serviceId;

    private Long userId(long label) {
        return users.get(label).getId();
    }

    private Long matchId;
    private Long otherMatchId;
    private List<Long> participantIds;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            matchParticipantRepository.deleteAllInBatch();
            matchRepository.deleteAllInBatch();
            users = MatchUserFixtures.create(entityManager, 11);
            serviceId = FacilityFixtures.create(entityManager, FacilityFixtures.newServiceId()).getId();
            MatchEntity match = matchRepository.save(MatchEntity.builder()
                    .title("삭제 대상").sportType(SportType.FUTSAL)
                    .maxParticipant(5).genderGroup(GenderGroup.MIXED).service(entityManager.getReference(ServiceEntity.class, serviceId))
                    .startAt(LocalDateTime.now().plusDays(1)).endAt(LocalDateTime.now().plusDays(1).plusHours(2))
                    .build());
            MatchEntity otherMatch = matchRepository.save(MatchEntity.builder()
                    .title("유지 대상").sportType(SportType.FUTSAL)
                    .maxParticipant(5).genderGroup(GenderGroup.MIXED).service(entityManager.getReference(ServiceEntity.class, serviceId))
                    .startAt(LocalDateTime.now().plusDays(1)).endAt(LocalDateTime.now().plusDays(1).plusHours(2))
                    .build());
            matchId = match.getId();
            otherMatchId = otherMatch.getId();
            participantIds = matchParticipantRepository.saveAll(List.of(
                    participant(match, 1L, MatchParticipantRole.OWNER),
                    participant(match, 2L, MatchParticipantRole.PARTICIPANT),
                    participant(match, 3L, MatchParticipantRole.PARTICIPANT)))
                    .stream().map(MatchParticipantEntity::getId).toList();
            matchParticipantRepository.save(participant(otherMatch, 1L, MatchParticipantRole.OWNER));
        });
    }

    @Test
    @DisplayName("[EM05-001/005] 매치와 모든 참가 기록을 DB에서 삭제하고 다른 매치는 유지한다")
    void deletesOnlyTargetMatchAndItsParticipants() {
        matchService.deleteMatch(matchId, userId(1L));

        assertThat(matchRepository.existsById(matchId)).isFalse();
        assertThat(matchParticipantRepository.countByMatch_Id(matchId)).isZero();
        assertThat(matchParticipantRepository.findAllById(participantIds)).isEmpty();
        assertOtherMatchIsUnchanged();
    }

    @ParameterizedTest
    @ValueSource(longs = {2L, 4L})
    @DisplayName("[EM05-003] 일반 참가자와 미참가자의 삭제 시도 후 DB가 유지된다")
    void forbiddenDeletePreservesData(Long userId) {
        assertThatThrownBy(() -> matchService.deleteMatch(matchId, userId(userId)))
                .isInstanceOf(MatchDeleteForbiddenException.class);

        assertTargetMatchIsUnchanged();
        assertOtherMatchIsUnchanged();
    }

    @Test
    @DisplayName("[EM05-002] 없는 매치를 삭제해도 기존 데이터가 유지된다")
    void missingMatchPreservesData() {
        assertThatThrownBy(() -> matchService.deleteMatch(Long.MAX_VALUE, userId(1L)))
                .isInstanceOf(MatchNotFoundException.class);

        assertTargetMatchIsUnchanged();
        assertOtherMatchIsUnchanged();
    }

    @Test
    @DisplayName("참가 기록 삭제 SQL 실행 후 매치 삭제가 실패하면 참가 기록도 복구된다")
    void matchDeleteFailureRollsBackParticipantDeletion() {
        doAnswer(invocation -> {
            // 참가 기록 DELETE를 DB에 반영한 다음 후속 삭제 실패를 재현한다.
            matchParticipantRepository.flush();
            assertThat(matchParticipantRepository.countByMatch_Id(matchId)).isZero();
            throw new IllegalStateException("test-only delete failure");
        }).when(matchRepository).delete(any(MatchEntity.class));

        assertThatThrownBy(() -> matchService.deleteMatch(matchId, userId(1L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("test-only delete failure");

        assertTargetMatchIsUnchanged();
        assertOtherMatchIsUnchanged();
    }

    private void assertTargetMatchIsUnchanged() {
        assertThat(matchRepository.existsById(matchId)).isTrue();
        assertThat(matchParticipantRepository.findAllById(participantIds))
                .extracting(MatchParticipantEntity::getId).containsExactlyInAnyOrderElementsOf(participantIds);
        assertThat(matchParticipantRepository.countByMatch_Id(matchId)).isEqualTo(3);
    }

    private void assertOtherMatchIsUnchanged() {
        assertThat(matchRepository.existsById(otherMatchId)).isTrue();
        assertThat(matchParticipantRepository.countByMatch_Id(otherMatchId)).isEqualTo(1);
    }

    private MatchParticipantEntity participant(MatchEntity match, Long userId, MatchParticipantRole role) {
        return MatchParticipantEntity.builder().match(match).user(users.get(userId)).role(role).build();
    }
}
