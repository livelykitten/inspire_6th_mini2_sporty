package com.example.sporty.features.exerciseMatching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import jakarta.persistence.EntityManager;
import com.example.sporty.features.users.domain.entity.UserEntity;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.support.MatchUserFixtures;
import com.example.sporty.support.FacilityFixtures;
import com.example.sporty.features.facilities.domain.entity.ServiceEntity;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.sporty.features.commons.exception.exerciseMatching.MatchAlreadyJoinedException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchAlreadyStartedException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchFullException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchNotFoundException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchRecruitmentClosedException;
import com.example.sporty.features.commons.exception.matches.MatchUserNotFoundException;
import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchParticipantResponseDto;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchEntity;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchParticipantEntity;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchParticipantRole;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;
import com.example.sporty.features.exerciseMatching.repository.MatchParticipantRepository;
import com.example.sporty.features.exerciseMatching.repository.MatchRepository;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.auto_quote_keyword=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(MatchService.class)
// 각 서비스 호출이 실제로 커밋/롤백된 뒤 별도 트랜잭션에서 결과를 확인한다.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MatchJoinIntegrationTest {

    @Autowired
    private MatchService matchService;

    @Autowired
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

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            matchParticipantRepository.deleteAllInBatch();
            matchRepository.deleteAllInBatch();
            users = MatchUserFixtures.create(entityManager, 11);
            serviceId = FacilityFixtures.create(entityManager, FacilityFixtures.newServiceId()).getId();
        });
    }

    @Test
    @DisplayName("[EM06-001] 참가 정보가 실제 DB에 저장되고 상세 조회의 인원과 참여 여부에 반영된다")
    void participationIsCommittedAndVisibleInDetail() {
        Long matchId = createMatch(5, MatchStatus.RECRUITING, LocalDateTime.now().plusDays(1));
        Long otherMatchId = createMatch(5, MatchStatus.RECRUITING, LocalDateTime.now().plusDays(1));

        MatchParticipantResponseDto result = matchService.joinMatch(matchId, userId(2L));

        MatchParticipantEntity saved = matchParticipantRepository.findById(result.getMatchParticipantId()).orElseThrow();
        assertThat(saved.getUser().getId()).isEqualTo(userId(2L));
        assertThat(saved.getRole()).isEqualTo(MatchParticipantRole.PARTICIPANT);
        assertThat(result.getMatchId()).isEqualTo(matchId);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertState(matchId, 2, MatchStatus.RECRUITING);
        assertState(otherMatchId, 1, MatchStatus.RECRUITING);
        var detail = matchService.getMatchDetail(matchId, userId(2L));
        assertThat(detail.getCurrentParticipantCount()).isEqualTo(2);
        assertThat(detail.getIsParticipant()).isTrue();
        assertThat(detail.getIsOwner()).isFalse();
    }

    @Test
    @DisplayName("OWNER 포함 10번째 참가자를 저장하면 CLOSED가 커밋되고 이후 신청은 거절된다")
    void tenthParticipantClosesRecruitment() {
        Long matchId = createMatch(10, MatchStatus.RECRUITING, LocalDateTime.now().plusDays(1));
        for (long userId = 2; userId <= 9; userId++) {
            matchService.joinMatch(matchId, userId(userId));
        }
        assertState(matchId, 9, MatchStatus.RECRUITING);

        matchService.joinMatch(matchId, userId(10L));

        assertState(matchId, 10, MatchStatus.CLOSED);
        assertThatThrownBy(() -> matchService.joinMatch(matchId, userId(11L)))
                .isInstanceOf(MatchRecruitmentClosedException.class);
        assertState(matchId, 10, MatchStatus.CLOSED);
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 2L})
    @DisplayName("[EM06-002/003] OWNER와 일반 참가자의 재신청은 기존 참가 기록과 상태를 유지한다")
    void duplicateParticipationPreservesData(Long userId) {
        Long matchId = createMatch(2, MatchStatus.RECRUITING, LocalDateTime.now().plusDays(1));
        matchService.joinMatch(matchId, userId(2L));
        List<Long> ids = participantIds(matchId);

        assertThatThrownBy(() -> matchService.joinMatch(matchId, userId(userId)))
                .isInstanceOf(MatchAlreadyJoinedException.class);

        assertThat(participantIds(matchId)).containsExactlyElementsOf(ids);
        assertState(matchId, 2, MatchStatus.CLOSED);
    }

    @Test
    @DisplayName("[EM06-004] RECRUITING이지만 이미 정원이 가득 찬 경우에도 참가 정보가 추가되지 않는다")
    void fullMatchPreservesData() {
        Long matchId = createMatch(1, MatchStatus.RECRUITING, LocalDateTime.now().plusDays(1));

        assertThatThrownBy(() -> matchService.joinMatch(matchId, userId(2L))).isInstanceOf(MatchFullException.class);

        assertState(matchId, 1, MatchStatus.RECRUITING);
    }

    @Test
    @DisplayName("[EM06-005] 모집 마감이면 참가 정보와 매치 상태가 유지된다")
    void closedMatchPreservesData() {
        Long matchId = createMatch(5, MatchStatus.CLOSED, LocalDateTime.now().plusDays(1));

        assertThatThrownBy(() -> matchService.joinMatch(matchId, userId(2L)))
                .isInstanceOf(MatchRecruitmentClosedException.class);

        assertState(matchId, 1, MatchStatus.CLOSED);
    }

    @Test
    @DisplayName("경기 시작 이후에는 자리가 남아 있어도 참가 정보가 추가되지 않는다")
    void startedMatchPreservesData() {
        Long matchId = createMatch(5, MatchStatus.RECRUITING, LocalDateTime.now().minusMinutes(1));

        assertThatThrownBy(() -> matchService.joinMatch(matchId, userId(2L)))
                .isInstanceOf(MatchAlreadyStartedException.class);

        assertState(matchId, 1, MatchStatus.RECRUITING);
    }

    @Test
    @DisplayName("[EM06-006] 없는 매치의 참여 요청은 다른 매치와 참가 기록을 변경하지 않는다")
    void missingMatchPreservesData() {
        Long matchId = createMatch(5, MatchStatus.RECRUITING, LocalDateTime.now().plusDays(1));

        assertThatThrownBy(() -> matchService.joinMatch(Long.MAX_VALUE, userId(2L)))
                .isInstanceOf(MatchNotFoundException.class);

        assertState(matchId, 1, MatchStatus.RECRUITING);
        assertThat(matchParticipantRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("참가자 저장과 CLOSED 변경 SQL 실행 후 트랜잭션 실패 시 두 변경 모두 롤백된다")
    void transactionFailureRollsBackParticipationAndClosure() {
        Long matchId = createMatch(2, MatchStatus.RECRUITING, LocalDateTime.now().plusDays(1));

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            matchService.joinMatch(matchId, userId(2L));
            matchRepository.flush();
            assertState(matchId, 2, MatchStatus.CLOSED);
            throw new IllegalStateException("test-only transaction failure");
        })).isInstanceOf(IllegalStateException.class).hasMessage("test-only transaction failure");

        assertState(matchId, 1, MatchStatus.RECRUITING);
        assertThat(matchParticipantRepository.existsByMatch_IdAndUserId(matchId, userId(2L))).isFalse();
    }

    @Test
    @DisplayName("마지막 한 자리에 두 사용자가 동시에 신청해도 한 명만 저장되고 모집이 마감된다")
    void concurrentRequestsCannotExceedCapacity() throws Exception {
        Long matchId = createMatch(2, MatchStatus.RECRUITING, LocalDateTime.now().plusDays(1));

        List<Object> results = joinConcurrently(matchId, 2L, 3L);

        assertThat(results.stream().filter(MatchParticipantResponseDto.class::isInstance).count()).isEqualTo(1);
        assertThat(results.stream().filter(MatchRecruitmentClosedException.class::isInstance).count()).isEqualTo(1);
        assertState(matchId, 2, MatchStatus.CLOSED);
    }

    @Test
    @DisplayName("같은 사용자가 동시에 두 번 신청해도 참가 기록은 한 개만 저장된다")
    void concurrentDuplicateRequestsCreateOnlyOneParticipant() throws Exception {
        Long matchId = createMatch(5, MatchStatus.RECRUITING, LocalDateTime.now().plusDays(1));

        List<Object> results = joinConcurrently(matchId, 2L, 2L);

        assertThat(results.stream().filter(MatchParticipantResponseDto.class::isInstance).count()).isEqualTo(1);
        assertThat(results.stream().filter(MatchAlreadyJoinedException.class::isInstance).count()).isEqualTo(1);
        assertState(matchId, 2, MatchStatus.RECRUITING);
        assertThat(matchParticipantRepository.findAllByMatch_IdOrderByIdAsc(matchId))
                .filteredOn(participant -> participant.getUser().getId().equals(userId(2L))).hasSize(1);
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 신청은 참가 기록과 모집 상태를 변경하지 않는다")
    void missingUserPreservesData() {
        Long matchId = createMatch(2, MatchStatus.RECRUITING, LocalDateTime.now().plusDays(1));
        List<Long> before = participantIds(matchId);

        assertThatThrownBy(() -> matchService.joinMatch(matchId, Long.MAX_VALUE))
                .isInstanceOf(MatchUserNotFoundException.class);

        assertThat(participantIds(matchId)).containsExactlyElementsOf(before);
        assertState(matchId, 1, MatchStatus.RECRUITING);
    }

    private List<Object> joinConcurrently(Long matchId, Long firstUserId, Long secondUserId) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Object> first = executor.submit(() -> joinAfterSignal(matchId, firstUserId, ready, start));
            Future<Object> second = executor.submit(() -> joinAfterSignal(matchId, secondUserId, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Object joinAfterSignal(Long matchId, Long userId, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 요청 시작 신호 대기 시간 초과");
        }
        try {
            return matchService.joinMatch(matchId, userId(userId));
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private Long createMatch(int maxParticipant, MatchStatus matchStatus, LocalDateTime startAt) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            MatchEntity match = matchRepository.save(MatchEntity.builder()
                    .title("참여 테스트").sportType(SportType.FUTSAL).maxParticipant(maxParticipant)
                    .service(entityManager.getReference(ServiceEntity.class, serviceId)).genderGroup(GenderGroup.MIXED)
                    .status(matchStatus).startAt(startAt).endAt(startAt.plusHours(2)).build());
            matchParticipantRepository.save(MatchParticipantEntity.builder()
                    .match(match).user(users.get(1L)).role(MatchParticipantRole.OWNER).build());
            return match.getId();
        });
    }

    private List<Long> participantIds(Long matchId) {
        return matchParticipantRepository.findAllByMatch_IdOrderByIdAsc(matchId).stream()
                .map(MatchParticipantEntity::getId).toList();
    }

    private void assertState(Long matchId, long count, MatchStatus status) {
        assertThat(matchParticipantRepository.countByMatch_Id(matchId)).isEqualTo(count);
        assertThat(matchRepository.findById(matchId).orElseThrow().getStatus()).isEqualTo(status);
    }
}
