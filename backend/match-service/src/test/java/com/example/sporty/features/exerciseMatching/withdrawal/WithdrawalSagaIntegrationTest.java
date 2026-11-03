package com.example.sporty.features.exerciseMatching.withdrawal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import com.example.sporty.features.commons.exception.matches.WithdrawnUserFoundException;
import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.client.*;
import com.example.sporty.features.exerciseMatching.domain.dto.*;
import com.example.sporty.features.exerciseMatching.domain.entity.*;
import com.example.sporty.features.exerciseMatching.domain.enums.*;
import com.example.sporty.features.exerciseMatching.repository.*;
import com.example.sporty.features.exerciseMatching.service.MatchService;
import com.example.sporty.support.FacilityFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.auto_quote_keyword=true",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql",
        "test.context-id=withdrawal-saga"
})
@Import({MatchService.class, MatchUserGuardService.class, WithdrawalService.class,
        WithdrawalTransactionService.class, WithdrawalCleanupHook.class, MatchOutboxSender.class,
        WithdrawalSagaIntegrationTest.JsonConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WithdrawalSagaIntegrationTest {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class JsonConfig {
        @Bean ObjectMapper objectMapper() { return JsonMapper.builder().findAndAddModules().build(); }
    }

    @Autowired MatchService matches;
    @Autowired WithdrawalService withdrawals;
    @Autowired MatchRepository matchRepository;
    @Autowired MatchParticipantRepository participants;
    @Autowired FacilityServiceSnapshotRepository facilities;
    @Autowired MatchUserGuardRepository guards;
    @MockitoSpyBean ProcessedEventRepository processed;
    @Autowired MatchOutboxRepository outbox;
    @Autowired MatchOutboxSender sender;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ObjectMapper mapper;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @MockitoBean UserServiceClient users;
    @MockitoBean MemberLookup memberLookup;
    @MockitoBean KafkaTemplate<String, String> kafka;
    @MockitoSpyBean WithdrawalCleanupHook hook;
    WithdrawalRequestedEvent event;
    Long ownedId, joinedId, unrelatedId;
    TransactionTemplate tx;

    @BeforeEach
    void setup() {
        tx = new TransactionTemplate(transactionManager);
        event = new WithdrawalRequestedEvent(UUID.randomUUID().toString(), LocalDateTime.now().withNano(0),
                UUID.randomUUID().toString(), 7L);
        tx.executeWithoutResult(status -> {
            participants.deleteAll();
            matchRepository.deleteAll();
            outbox.deleteAll();
            processed.deleteAll();
            guards.deleteAll();
            facilities.deleteAll();
            entityManager.flush();
            entityManager.clear();
            var facility = facilities.save(FacilityFixtures.service(1L, true));
            ownedId = newMatch(facility, "owned", 7L, 8L);
            joinedId = newMatch(facility, "joined", 8L, 7L);
            unrelatedId = newMatch(facility, "unrelated", 8L, 9L);
        });
        lenient().when(users.getUserStatus(7L)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new UserStatusResponseDto(7L, "WITHDRAWAL_PENDING", event.sagaId());
        });
    }

    @Test
    void commitsCleanupGuardProcessedMarkerAndCompletionOutboxTogether() throws Exception {
        withdrawals.handle(event);
        assertThat(matchRepository.existsById(ownedId)).isFalse();
        assertThat(participants.countByMatch_Id(ownedId)).isZero();
        assertThat(participants.existsByMatch_IdAndUserId(joinedId, 7L)).isFalse();
        assertThat(participants.countByMatch_Id(joinedId)).isEqualTo(1);
        assertThat(participants.countByMatch_Id(unrelatedId)).isEqualTo(2);
        assertThat(matchRepository.findById(joinedId).orElseThrow().getStatus()).isEqualTo(MatchStatus.RECRUITING);
        assertThat(guards.findById(7L).orElseThrow().isBlocked()).isTrue();
        assertThat(processed.existsById(event.eventId())).isTrue();
        var row = outbox.findAll().get(0);
        assertThat(outbox.count()).isEqualTo(1);
        assertThat(row.getStatus()).isEqualTo("PENDING");
        assertThat(row.getMessageKey()).isEqualTo("7");
        assertThat(row.getTopic()).isEqualTo(WithdrawalCompletedEvent.TOPIC);
        var completed = mapper.readValue(row.getPayload(), WithdrawalCompletedEvent.class);
        assertThat(mapper.readTree(row.getPayload()).get("occurredAt").isTextual()).isTrue();
        assertThat(completed.eventId()).isEqualTo(row.getEventId()).isNotEqualTo(event.eventId());
        assertThat(completed.sagaId()).isEqualTo(event.sagaId());
        assertThat(completed.userId()).isEqualTo(7L);
        assertThat(completed.occurredAt().getNano()).isZero();
        assertThat(completed.deletedMatchIds()).containsExactly(ownedId);
        assertThat(completed.leftMatchIds()).containsExactly(joinedId);
    }

    @Test
    void duplicateAfterCommitDoesNotCallFeignOrCreateNewResult() {
        withdrawals.handle(event);
        String payload = outbox.findAll().get(0).getPayload();
        clearInvocations(users);
        withdrawals.handle(event);
        verifyNoInteractions(users);
        assertThat(outbox.count()).isEqualTo(1);
        assertThat(processed.count()).isEqualTo(1);
        assertThat(outbox.findAll().get(0).getPayload()).isEqualTo(payload);
    }

    @Test
    void simultaneousDuplicatesAreSerializedAndProduceOneResult() throws Exception {
        var bothLookedUp = new CyclicBarrier(2);
        when(users.getUserStatus(7L)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            bothLookedUp.await(5, TimeUnit.SECONDS);
            return new UserStatusResponseDto(7L, "WITHDRAWAL_PENDING", event.sagaId());
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> withdrawals.handle(event));
            var second = executor.submit(() -> withdrawals.handle(event));
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
        assertThat(processed.count()).isEqualTo(1);
        assertThat(outbox.count()).isEqualTo(1);
        assertThat(guards.findById(7L).orElseThrow().isBlocked()).isTrue();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void cleanupWinningRaceBlocksCreateOrJoinWithStaleActiveLookup(boolean create) throws Exception {
        var lookedUp = new CountDownLatch(1);
        var resume = new CountDownLatch(1);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            lookedUp.countDown();
            if (!resume.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("lookup barrier timed out");
            return null;
        }).when(memberLookup).validateActive(7L);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var pendingRequest = executor.submit(() -> {
                if (create) matches.createMatch(request(), 7L);
                else matches.joinMatch(joinedId, 7L);
            });
            assertThat(lookedUp.await(5, TimeUnit.SECONDS)).isTrue();
            withdrawals.handle(event);
            resume.countDown();
            assertThatThrownBy(() -> pendingRequest.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(WithdrawnUserFoundException.class);
        } finally {
            resume.countDown();
            executor.shutdownNow();
        }
        assertThat(matchRepository.count()).isEqualTo(2);
        assertThat(participants.findAllByUserIdOrderByMatch_StartAtDescMatch_IdDesc(7L)).isEmpty();
        assertThat(outbox.count()).isEqualTo(1);
    }

    @Test
    void listenerAcknowledgesAfterDatabaseCommit() throws Exception {
        var ack = mock(org.springframework.kafka.support.Acknowledgment.class);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(processed.existsById(event.eventId())).isTrue();
            assertThat(outbox.count()).isEqualTo(1);
            assertThat(guards.findById(7L).orElseThrow().isBlocked()).isTrue();
            return null;
        }).when(ack).acknowledge();
        new WithdrawalRequestedListener(mapper, withdrawals).consume(mapper.writeValueAsString(event), ack);
        verify(ack).acknowledge();
    }

    @Test
    void staleSagaOrNonPendingStatusDoesNotCleanOrRecordSuccess() {
        when(users.getUserStatus(7L)).thenReturn(new UserStatusResponseDto(7L, "WITHDRAWAL_PENDING", "different"));
        withdrawals.handle(event);
        when(users.getUserStatus(7L)).thenReturn(new UserStatusResponseDto(7L, "ACTIVE", null));
        withdrawals.handle(event);
        assertUnchanged();
    }

    @Test
    void memberWithoutMatchesStillGetsCompletedWithEmptyIdLists() throws Exception {
        var empty = new WithdrawalRequestedEvent(UUID.randomUUID().toString(), event.occurredAt(), event.sagaId(), 99L);
        when(users.getUserStatus(99L)).thenReturn(new UserStatusResponseDto(99L, "WITHDRAWAL_PENDING", empty.sagaId()));
        withdrawals.handle(empty);
        var completed = mapper.readValue(outbox.findAll().get(0).getPayload(), WithdrawalCompletedEvent.class);
        assertThat(completed.deletedMatchIds()).isEmpty();
        assertThat(completed.leftMatchIds()).isEmpty();
        assertThat(guards.findById(99L).orElseThrow().isBlocked()).isTrue();
        assertThat(matchRepository.count()).isEqualTo(3);
    }

    @Test
    void lookupFailurePropagatesAndDoesNotRecordSuccess() {
        when(users.getUserStatus(7L)).thenThrow(new IllegalStateException("user-service unavailable"));
        assertThatThrownBy(() -> withdrawals.handle(event)).isInstanceOf(IllegalStateException.class);
        assertUnchanged();
    }

    @Test
    void failureAfterDeletionRollsBackCleanupGuardHistoryAndOutbox() {
        doThrow(new IllegalStateException("injected after cleanup")).when(hook).afterCleanup(event);
        assertThatThrownBy(() -> withdrawals.handle(event)).hasMessage("injected after cleanup");
        assertUnchanged();
        assertThat(guards.count()).isZero();
    }

    @Test
    void failureAfterOutboxInsertAlsoRollsBackEverything() {
        doThrow(new IllegalStateException("marker insert failed")).when(processed)
                .insert(event.eventId(), WithdrawalRequestedEvent.TOPIC);
        assertThatThrownBy(() -> withdrawals.handle(event)).hasMessage("marker insert failed");
        assertUnchanged();
        assertThat(guards.count()).isZero();
    }

    @Test
    void guardBlocksCreateAndJoinEvenIfEarlierActiveCheckPassed() {
        withdrawals.handle(event);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return null;
        }).when(memberLookup).validateActive(7L);
        assertThatThrownBy(() -> matches.createMatch(request(), 7L)).isInstanceOf(WithdrawnUserFoundException.class);
        assertThatThrownBy(() -> matches.joinMatch(joinedId, 7L)).isInstanceOf(WithdrawnUserFoundException.class);
        assertThat(guards.findById(7L).orElseThrow().isBlocked()).isTrue();
        assertThat(participants.existsByMatch_IdAndUserId(joinedId, 7L)).isFalse();
    }

    @Test
    void createAndJoinUseUnblockedGuardAndCallFeignOutsideTransaction() {
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return null;
        }).when(memberLookup).validateActive(9L);
        var created = matches.createMatch(request(), 9L);
        matches.leaveMatch(joinedId, 7L);
        matches.joinMatch(joinedId, 9L);
        assertThat(matchRepository.existsById(created)).isTrue();
        assertThat(participants.existsByMatch_IdAndUserId(joinedId, 9L)).isTrue();
        assertThat(guards.findById(9L).orElseThrow().isBlocked()).isFalse();
    }

    @Test
    void outboxRetriesIdenticalPayloadAndMarksSentOnlyAfterKafkaAck() {
        withdrawals.handle(event);
        var before = outbox.findAll().get(0);
        when(kafka.send(before.getTopic(), "7", before.getPayload()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")))
                .thenReturn(CompletableFuture.completedFuture(null));
        assertThat(sender.publishNext()).isFalse();
        var pending = outbox.findById(before.getId()).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo("PENDING");
        assertThat(pending.getAttemptCount()).isEqualTo(1);
        assertThat(pending.getSentAt()).isNull();
        assertThat(sender.publishNext()).isTrue();
        var sent = outbox.findById(before.getId()).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo("SENT");
        assertThat(sent.getSentAt()).isNotNull();
        assertThat(sent.getAttemptCount()).isEqualTo(1);
        assertThat(sent.getEventId()).isEqualTo(before.getEventId());
        assertThat(sent.getPayload()).isEqualTo(before.getPayload());
        assertThat(sender.publishNext()).isFalse();
        verify(kafka, times(2)).send(before.getTopic(), "7", before.getPayload());
    }

    void assertUnchanged() {
        assertThat(matchRepository.count()).isEqualTo(3);
        assertThat(participants.count()).isEqualTo(6);
        assertThat(processed.count()).isZero();
        assertThat(outbox.count()).isZero();
    }

    Long newMatch(FacilityServiceSnapshotEntity facility, String title, Long owner, Long joined) {
        var start = LocalDateTime.now().plusDays(2);
        var match = matchRepository.save(MatchEntity.builder().title(title).service(facility)
                .startAt(start).endAt(start.plusHours(1)).maxParticipant(2).status(MatchStatus.CLOSED)
                .sportType(SportType.SOCCER).genderGroup(GenderGroup.MIXED).build());
        participants.save(MatchParticipantEntity.builder().match(match).userId(owner).role(MatchParticipantRole.OWNER).build());
        participants.save(MatchParticipantEntity.builder().match(match).userId(joined).role(MatchParticipantRole.PARTICIPANT).build());
        return match.getId();
    }

    MatchCreateRequestDto request() {
        var start = LocalDateTime.now().plusDays(1);
        return MatchCreateRequestDto.builder().title("created").serviceId(1L).startAt(start)
                .endAt(start.plusHours(1)).maxParticipant(4).skillLevel(SkillLevel.BEGINNER)
                .sportType(SportType.SOCCER).genderGroup(GenderGroup.MIXED).build();
    }
}
