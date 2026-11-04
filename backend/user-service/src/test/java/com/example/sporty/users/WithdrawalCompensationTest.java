package com.example.sporty.users;

import com.example.sporty.features.users.domain.dto.WithdrawalCompletedEvent;
import com.example.sporty.features.users.domain.dto.WithdrawalFailedEvent;
import com.example.sporty.features.users.domain.entity.Gender;
import com.example.sporty.features.users.domain.entity.UserEntity;
import com.example.sporty.features.users.domain.entity.UserStatus;
import com.example.sporty.features.users.repository.UserRepository;
import com.example.sporty.features.users.service.WithdrawalCompensationService;
import com.example.sporty.features.users.service.WithdrawalCompletionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.config.import=",
        "spring.cloud.config.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:w32;MODE=MariaDB;NON_KEYWORDS=USER",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({WithdrawalCompensationService.class, WithdrawalCompletionService.class})
class WithdrawalCompensationTest {

    @Autowired UserRepository users;
    @Autowired WithdrawalCompensationService compensation;
    @Autowired WithdrawalCompletionService completion;

    @Test
    void matchingFailureRestoresActiveAndClearsSagaWithoutWithdrawalTime() {
        var user = pending();
        assertThat(compensation.compensate(failure(user))).isTrue();
        var restored = users.findById(user.getId()).orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(restored.getWithdrawalSagaId()).isNull();
        assertThat(restored.getWithdrawnAt()).isNull();
        assertThat(restored.getEmail()).isEqualTo(user.getEmail());
        assertThat(restored.getPassword()).isEqualTo(user.getPassword());
    }

    @Test
    void duplicateFailureDoesNotChangeRestoredMember() {
        var event = failure(pending());
        assertThat(compensation.compensate(event)).isTrue();
        var first = users.findById(event.userId()).orElseThrow().getUpdatedAt();
        assertThat(compensation.compensate(event)).isFalse();
        var restored = users.findById(event.userId()).orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(restored.getWithdrawalSagaId()).isNull();
        assertThat(restored.getUpdatedAt()).isEqualTo(first);
    }

    @Test
    void staleFailureDoesNotCancelNewWithdrawalRequest() {
        var user = pending();
        var old = failure(user);
        assertThat(compensation.compensate(old)).isTrue();
        var newSaga = UUID.randomUUID().toString();
        assertThat(users.startWithdrawal(user.getId(), newSaga,
                UserStatus.ACTIVE, UserStatus.WITHDRAWAL_PENDING)).isEqualTo(1);
        assertThat(compensation.compensate(old)).isFalse();
        var current = users.findById(user.getId()).orElseThrow();
        assertThat(current.getStatus()).isEqualTo(UserStatus.WITHDRAWAL_PENDING);
        assertThat(current.getWithdrawalSagaId()).isEqualTo(newSaga);
    }

    @Test
    void differentSagaDoesNotChangePendingMember() {
        var user = pending();
        assertThat(compensation.compensate(new WithdrawalFailedEvent(
                UUID.randomUUID().toString(), "2026-11-04T10:00:00",
                UUID.randomUUID().toString(), user.getId(), "cleanup failed"))).isFalse();
        var current = users.findById(user.getId()).orElseThrow();
        assertThat(current.getStatus()).isEqualTo(UserStatus.WITHDRAWAL_PENDING);
        assertThat(current.getWithdrawalSagaId()).isEqualTo(user.getWithdrawalSagaId());
    }

    @Test
    void failureAfterSuccessfulCompletionCannotReactivateWithdrawnMember() {
        var user = pending();
        assertThat(completion.complete(success(user))).isTrue();
        var timestamp = users.findById(user.getId()).orElseThrow().getWithdrawnAt();
        assertThat(compensation.compensate(failure(user))).isFalse();
        var current = users.findById(user.getId()).orElseThrow();
        assertThat(current.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(current.getWithdrawnAt()).isEqualTo(timestamp);
    }

    @Test
    void lateSuccessAfterCompensationCannotWithdrawRestoredMember() {
        var user = pending();
        assertThat(compensation.compensate(failure(user))).isTrue();
        assertThat(completion.complete(success(user))).isFalse();
        assertThat(users.findById(user.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void activeMemberIsNotChangedEvenIfSagaFieldMatches() {
        var saga = UUID.randomUUID().toString();
        var user = users.saveAndFlush(UserEntity.builder()
                .email(UUID.randomUUID() + "@test.com").password("unchanged-hash")
                .gender(Gender.MALE).status(UserStatus.ACTIVE)
                .withdrawalSagaId(saga).build());
        assertThat(compensation.compensate(failure(user))).isFalse();
        assertThat(users.findById(user.getId()).orElseThrow().getWithdrawalSagaId())
                .isEqualTo(saga);
    }

    @Test
    void missingMemberIsIgnored() {
        assertThat(compensation.compensate(new WithdrawalFailedEvent(
                UUID.randomUUID().toString(), "2026-11-04T10:00:00",
                UUID.randomUUID().toString(), Long.MAX_VALUE, "cleanup failed"))).isFalse();
    }

    @Test
    void invalidIdentifiersAreRejectedWithoutChangingPendingMember() {
        var user = pending();
        var valid = failure(user);
        assertThatThrownBy(() -> compensation.compensate(null))
                .isInstanceOf(IllegalArgumentException.class);
        for (var event : List.of(
                new WithdrawalFailedEvent(null, null, valid.sagaId(), user.getId(), null),
                new WithdrawalFailedEvent(" ", null, valid.sagaId(), user.getId(), null),
                new WithdrawalFailedEvent(valid.eventId(), null, null, user.getId(), null),
                new WithdrawalFailedEvent(valid.eventId(), null, " ", user.getId(), null),
                new WithdrawalFailedEvent(valid.eventId(), null, valid.sagaId(), null, null),
                new WithdrawalFailedEvent(valid.eventId(), null, valid.sagaId(), 0L, null),
                new WithdrawalFailedEvent(valid.eventId(), null, valid.sagaId(), -1L, null))) {
            assertThatThrownBy(() -> compensation.compensate(event))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(users.findById(user.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.WITHDRAWAL_PENDING);
    }

    private UserEntity pending() {
        return users.saveAndFlush(UserEntity.builder()
                .email(UUID.randomUUID() + "@test.com").password("unchanged-hash")
                .gender(Gender.MALE).status(UserStatus.WITHDRAWAL_PENDING)
                .withdrawalSagaId(UUID.randomUUID().toString()).build());
    }

    private WithdrawalFailedEvent failure(UserEntity user) {
        return new WithdrawalFailedEvent(UUID.randomUUID().toString(),
                "2026-11-04T10:00:00", user.getWithdrawalSagaId(), user.getId(), "cleanup failed");
    }

    private WithdrawalCompletedEvent success(UserEntity user) {
        return new WithdrawalCompletedEvent(UUID.randomUUID().toString(),
                "2026-11-04T10:00:00", user.getWithdrawalSagaId(), user.getId(), List.of(), List.of());
    }
}
