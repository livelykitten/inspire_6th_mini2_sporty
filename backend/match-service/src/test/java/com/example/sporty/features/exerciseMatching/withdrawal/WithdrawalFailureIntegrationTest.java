package com.example.sporty.features.exerciseMatching.withdrawal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.client.*;
import com.example.sporty.features.exerciseMatching.domain.dto.UserStatusResponseDto;
import com.example.sporty.features.exerciseMatching.domain.entity.*;
import com.example.sporty.features.exerciseMatching.domain.enums.*;
import com.example.sporty.features.exerciseMatching.repository.*;
import com.example.sporty.features.exerciseMatching.service.MatchService;
import com.example.sporty.support.FacilityFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.auto_quote_keyword=true",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql",
        "saga.fail-cleanup=true", "test.context-id=withdrawal-failure"
})
@Import({MatchService.class, MatchUserGuardService.class, WithdrawalService.class, WithdrawalTransactionService.class,
        WithdrawalCleanupHook.class, WithdrawalFailureService.class, WithdrawalOutcomeService.class,
        WithdrawalExhaustedRecoverer.class, WithdrawalDltPublisher.class, MatchOutboxSender.class,
        WithdrawalFailureIntegrationTest.JsonConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WithdrawalFailureIntegrationTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class JsonConfig {
        @Bean ObjectMapper mapper() { return JsonMapper.builder().findAndAddModules().build(); }
    }
    @Autowired WithdrawalService service;
    @Autowired WithdrawalFailureService failures;
    @Autowired WithdrawalExhaustedRecoverer recoverer;
    @Autowired MatchOutboxSender sender;
    @Autowired MatchUserGuardService guardService;
    @Autowired MatchUserGuardRepository guards;
    @Autowired MatchRepository matches;
    @Autowired MatchParticipantRepository participants;
    @Autowired FacilityServiceSnapshotRepository facilities;
    @Autowired MatchOutboxRepository outbox;
    @Autowired ObjectMapper mapper;
    @Autowired jakarta.persistence.EntityManager em;
    @MockitoSpyBean ProcessedEventRepository processed;
    @MockitoSpyBean PlatformTransactionManager transactionManager;
    @MockitoBean UserServiceClient users;
    @MockitoBean MemberLookup lookup;
    @MockitoBean KafkaTemplate<String, String> kafka;
    TransactionTemplate tx;
    WithdrawalRequestedEvent event;

    @BeforeEach
    void setup() {
        tx = new TransactionTemplate(transactionManager);
        event = new WithdrawalRequestedEvent(UUID.randomUUID().toString(), LocalDateTime.now().withNano(0),
                UUID.randomUUID().toString(), 7L);
        tx.executeWithoutResult(status -> {
            participants.deleteAll(); matches.deleteAll(); outbox.deleteAll(); processed.deleteAll();
            guards.deleteAll(); facilities.deleteAll(); em.flush(); em.clear();
            var facility = facilities.save(FacilityFixtures.service(1L, true));
            match(facility, "owned", 7L, 8L);
            match(facility, "joined", 8L, 7L);
            guardService.requireUnblocked(7L);
        });
        lenient().when(users.getUserStatus(7L)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new UserStatusResponseDto(7L, "WITHDRAWAL_PENDING", event.sagaId());
        });
        clearInvocations(users, kafka); // Exclude Spring's bean lifecycle calls from request assertions.
    }

    @Test
    @SuppressWarnings("unchecked")
    void fourRealRollbackAttemptsThenCompensationCommitBeforeRecoveredOffsetCommit() throws Exception {
        var handler = new WithdrawalKafkaConfiguration().withdrawalErrorHandler(recoverer);
        Consumer<String, String> consumer = mock(Consumer.class);
        var container = mock(MessageListenerContainer.class);
        var props = new ContainerProperties(WithdrawalRequestedEvent.TOPIC);
        props.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        props.setSyncCommits(true);
        props.setSyncCommitTimeout(Duration.ofSeconds(5));
        when(container.getContainerProperties()).thenReturn(props);
        var record = record();
        var ack = mock(org.springframework.kafka.support.Acknowledgment.class);
        var listener = new WithdrawalRequestedListener(mapper, service);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertCompensated();
            return null;
        }).when(consumer).commitSync(anyMap(), any(Duration.class));
        for (int i = 0; i < 4; i++) {
            var failure = catchThrowableOfType(() -> listener.consume(record.value(), ack), WithdrawalRolledBackException.class);
            assertRestored();
            assertThat(outbox.count()).isZero();
            if (i < 3) {
                assertThatThrownBy(() -> handler.handleRemaining(failure, List.of(record), consumer, container))
                        .isInstanceOf(RuntimeException.class).hasMessage("Record in retry and not yet recovered");
                verify(consumer, never()).commitSync(anyMap(), any(Duration.class));
            } else {
                handler.handleRemaining(failure, List.of(record), consumer, container);
            }
        }
        verify(users, times(5)).getUserStatus(7L); // Four attempts plus recovery validation.
        verify(consumer).commitSync(argThat(offsets -> offsets.get(new TopicPartition(record.topic(), 0)).offset() == 13L),
                any(Duration.class));
        verifyNoInteractions(ack, kafka);
        var failed = mapper.readValue(outbox.findAll().get(0).getPayload(), WithdrawalFailedEvent.class);
        assertThat(failed.userId()).isEqualTo(7L);
        assertThat(failed.sagaId()).isEqualTo(event.sagaId());
        assertThat(failed.reason()).isEqualTo("MATCH_CLEANUP_FAILED");
        assertThat(failed.eventId()).isNotEqualTo(event.eventId());
        assertThat(failed.occurredAt().getNano()).isZero();
        listener.consume(record.value(), ack);
        verify(ack).acknowledge();
        assertCompensated();
    }

    @Test
    void concurrentRecoveryCreatesOnlyOneCompensation() throws Exception {
        var failure = fail();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> failures.recordFailure(event, failure));
            var second = executor.submit(() -> failures.recordFailure(event, failure));
            first.get(10, TimeUnit.SECONDS); second.get(10, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
        assertCompensated();
    }

    @Test
    void compensationMarkerFailureRollsBackItsOutboxAndGoesToDlt() throws Exception {
        var failure = fail();
        doThrow(new IllegalStateException("marker DB failure")).when(processed).insert(event.eventId(), WithdrawalRequestedEvent.TOPIC);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        recoverer.accept(record(), failure);
        assertRestored();
        assertThat(outbox.count()).isZero();
        assertThat(processed.count()).isZero();
        verify(kafka).send(any(ProducerRecord.class));
    }

    @Test
    void plainLookupFailureAndUnknownCommitNeverGenerateCompensation() throws Exception {
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        when(users.getUserStatus(7L)).thenThrow(new IllegalStateException("Feign failed"));
        var lookupFailure = catchThrowableOfType(() -> service.handle(event), IllegalStateException.class);
        recoverer.accept(record(), lookupFailure);
        recoverer.accept(record(), new WithdrawalCommitUncertainException(new TransactionSystemException("unknown commit")));
        assertThat(outbox.count()).isZero();
        assertThat(processed.count()).isZero();
        assertRestored();
        verify(kafka, times(2)).send(any(ProducerRecord.class));
    }

    @Test
    void successfulSagaCannotBeCompensatedEvenWithDifferentEventId() throws Exception {
        var failure = fail();
        tx.executeWithoutResult(status -> {
            guardService.lock(7L).block();
            var completed = new WithdrawalCompletedEvent(UUID.randomUUID().toString(), event.occurredAt(),
                    event.sagaId(), 7L, List.of(), List.of());
            try {
                outbox.save(new MatchOutbox(completed.eventId(), WithdrawalCompletedEvent.TOPIC, 7L,
                        mapper.writeValueAsString(completed)));
            } catch (Exception e) { throw new IllegalStateException(e); }
            processed.insert(UUID.randomUUID().toString(), WithdrawalRequestedEvent.TOPIC);
        });
        recoverer.accept(record(), failure);
        assertThat(outbox.findAll()).extracting(MatchOutbox::getTopic).containsExactly(WithdrawalCompletedEvent.TOPIC);
        assertThat(guards.findById(7L).orElseThrow().isBlocked()).isTrue();
        verifyNoInteractions(kafka);
    }

    @Test
    void compensationOutboxRetriesWithSameEventAndPayload() throws Exception {
        recoverer.accept(record(), fail());
        var before = outbox.findAll().get(0);
        when(kafka.send(before.getTopic(), "7", before.getPayload()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("offline")))
                .thenReturn(CompletableFuture.completedFuture(null));
        assertThat(sender.publishNext()).isFalse();
        assertThat(outbox.findAll().get(0).getStatus()).isEqualTo("PENDING");
        assertThat(sender.publishNext()).isTrue();
        var after = outbox.findAll().get(0);
        assertThat(after.getStatus()).isEqualTo("SENT");
        assertThat(after.getPayload()).isEqualTo(before.getPayload());
        assertThat(after.getEventId()).isEqualTo(before.getEventId());
        verify(kafka, times(2)).send(before.getTopic(), "7", before.getPayload());
    }

    WithdrawalRolledBackException fail() {
        return catchThrowableOfType(() -> service.handle(event), WithdrawalRolledBackException.class);
    }

    ConsumerRecord<String, String> record() throws Exception {
        return new ConsumerRecord<>(WithdrawalRequestedEvent.TOPIC, 0, 12L, "7", mapper.writeValueAsString(event));
    }

    void assertRestored() {
        assertThat(matches.count()).isEqualTo(2);
        assertThat(participants.count()).isEqualTo(4);
        assertThat(guards.findById(7L).orElseThrow().isBlocked()).isFalse();
    }

    void assertCompensated() {
        assertRestored();
        assertThat(processed.count()).isEqualTo(1);
        assertThat(outbox.count()).isEqualTo(1);
        assertThat(outbox.findAll().get(0).getTopic()).isEqualTo(WithdrawalFailedEvent.TOPIC);
    }

    void match(FacilityServiceSnapshotEntity facility, String title, Long owner, Long joined) {
        var start = LocalDateTime.now().plusDays(2);
        var match = matches.save(MatchEntity.builder().title(title).service(facility).startAt(start)
                .endAt(start.plusHours(1)).maxParticipant(2).status(MatchStatus.CLOSED)
                .sportType(SportType.SOCCER).genderGroup(GenderGroup.MIXED).build());
        participants.save(MatchParticipantEntity.builder().match(match).userId(owner).role(MatchParticipantRole.OWNER).build());
        participants.save(MatchParticipantEntity.builder().match(match).userId(joined).role(MatchParticipantRole.PARTICIPANT).build());
    }
}
