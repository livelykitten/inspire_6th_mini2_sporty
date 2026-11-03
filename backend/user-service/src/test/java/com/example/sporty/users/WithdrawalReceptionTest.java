package com.example.sporty.users;

import com.example.sporty.features.commons.exception.users.UserAlreadyWithdrawnException;
import com.example.sporty.features.commons.exception.users.WithdrawalAlreadyPendingException;
import com.example.sporty.features.users.domain.dto.WithdrawalCompletedEvent;
import com.example.sporty.features.users.domain.entity.Gender;
import com.example.sporty.features.users.domain.entity.UserEntity;
import com.example.sporty.features.users.domain.entity.UserOutbox;
import com.example.sporty.features.users.domain.entity.UserStatus;
import com.example.sporty.features.users.repository.UserOutboxRepository;
import com.example.sporty.features.users.repository.UserRepository;
import com.example.sporty.features.users.service.UserService;
import com.example.sporty.features.users.service.WithdrawalCompletionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@DataJpaTest(properties = {
        "spring.config.import=",
        "spring.cloud.config.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:w22;MODE=MariaDB;NON_KEYWORDS=USER",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import({
        UserService.class,
        WithdrawalCompletionService.class,
        WithdrawalReceptionTest.TestBeans.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WithdrawalReceptionTest {

    @Autowired
    UserService userService;

    @Autowired
    UserRepository userRepository;

    @MockitoSpyBean
    UserOutboxRepository outboxRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    WithdrawalCompletionService completionService;

    @TestConfiguration
    static class TestBeans {

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void receptionStoresPendingStateAndMatchingOutbox() throws Exception {
        Long userId = createUser(UserStatus.ACTIVE);
        authenticate(userId);

        userService.withdrawal("Test1234!");

        var user = userRepository.findById(userId).orElseThrow();

        assertThat(user.getStatus())
                .isEqualTo(UserStatus.WITHDRAWAL_PENDING);
        assertThat(user.getWithdrawalSagaId()).isNotBlank();
        assertThat(user.getWithdrawnAt()).isNull();

        var events = outboxRepository.findAll();
        assertThat(events).hasSize(1);

        var outbox = events.get(0);

        assertThat(outbox.getStatus()).isEqualTo("PENDING");
        assertThat(outbox.getTopic())
                .isEqualTo("user.withdrawal.requested");
        assertThat(outbox.getMessageKey())
                .isEqualTo(userId.toString());

        var payload = objectMapper.readTree(outbox.getPayload());

        assertThat(payload.get("eventId").asText())
                .isEqualTo(outbox.getEventId());
        assertThat(payload.get("sagaId").asText())
                .isEqualTo(user.getWithdrawalSagaId());
        assertThat(payload.get("userId").asLong())
                .isEqualTo(userId);
    }

    @Test
    void repeatedRequestDoesNotCreateAnotherSagaOrOutbox() {
        Long userId = createUser(UserStatus.ACTIVE);
        authenticate(userId);

        userService.withdrawal("Test1234!");

        String sagaId = userRepository.findById(userId)
                .orElseThrow()
                .getWithdrawalSagaId();

        assertThatThrownBy(() -> userService.withdrawal("Test1234!"))
                .isInstanceOf(WithdrawalAlreadyPendingException.class);

        assertThat(outboxRepository.count()).isEqualTo(1);
        assertThat(userRepository.findById(userId)
                .orElseThrow()
                .getWithdrawalSagaId())
                .isEqualTo(sagaId);
    }

    @Test
    void concurrentRequestsCreateOnlyOneSagaAndOutbox() throws Exception {
        Long userId = createUser(UserStatus.ACTIVE);

        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        Callable<String> request = () -> {
            try {
                // 인증 정보는 각 작업 스레드에 따로 설정한다.
                authenticate(userId);
                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError(
                            "동시 요청 시작 대기 시간이 초과됐습니다."
                    );
                }

                try {
                    userService.withdrawal("Test1234!");
                    return "ACCEPTED";
                } catch (WithdrawalAlreadyPendingException exception) {
                    return "ALREADY_PENDING";
                }
            } finally {
                SecurityContextHolder.clearContext();
            }
        };

        try {
            var first = executor.submit(request);
            var second = executor.submit(request);

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)
            )).containsExactlyInAnyOrder(
                    "ACCEPTED",
                    "ALREADY_PENDING"
            );

            var user = userRepository.findById(userId).orElseThrow();

            assertThat(user.getStatus())
                    .isEqualTo(UserStatus.WITHDRAWAL_PENDING);
            assertThat(user.getWithdrawalSagaId()).isNotBlank();
            assertThat(user.getWithdrawnAt()).isNull();

            var outboxes = outboxRepository.findAll();
            assertThat(outboxes).hasSize(1);

            var outbox = outboxes.get(0);
            var payload = objectMapper.readTree(outbox.getPayload());

            assertThat(outbox.getStatus()).isEqualTo("PENDING");
            assertThat(outbox.getTopic())
                    .isEqualTo("user.withdrawal.requested");
            assertThat(outbox.getMessageKey())
                    .isEqualTo(userId.toString());

            assertThat(payload.get("userId").asLong())
                    .isEqualTo(userId);
            assertThat(payload.get("sagaId").asText())
                    .isEqualTo(user.getWithdrawalSagaId());
            assertThat(payload.get("eventId").asText())
                    .isEqualTo(outbox.getEventId());
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void withdrawnUserCannotStartSaga() {
        Long userId = createUser(UserStatus.WITHDRAWN);
        authenticate(userId);

        assertThatThrownBy(() -> userService.withdrawal("Test1234!"))
                .isInstanceOf(UserAlreadyWithdrawnException.class);

        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void outboxFailureRollsBackMemberState() {
        Long userId = createUser(UserStatus.ACTIVE);
        authenticate(userId);

        doThrow(new IllegalStateException("테스트용 저장 실패"))
                .when(outboxRepository)
                .save(any(UserOutbox.class));

        assertThatThrownBy(() -> userService.withdrawal("Test1234!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("테스트용 저장 실패");

        var user = userRepository.findById(userId).orElseThrow();

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getWithdrawalSagaId()).isNull();
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void matchingCompletionConfirmsWithdrawal() {
        Long userId = createUser(UserStatus.ACTIVE);
        authenticate(userId);

        userService.withdrawal("Test1234!");

        String sagaId = userRepository.findById(userId)
                .orElseThrow()
                .getWithdrawalSagaId();

        var event = new WithdrawalCompletedEvent(
                UUID.randomUUID().toString(),
                "2026-11-03T17:20:00",
                sagaId,
                userId,
                List.of(),
                List.of()
        );

        assertThat(completionService.complete(event)).isTrue();

        var user = userRepository.findById(userId).orElseThrow();

        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(user.getWithdrawalSagaId()).isNull();
        assertThat(user.getWithdrawnAt()).isNotNull();
    }

    @Test
    void oldSagaCompletionDoesNotChangeCurrentRequest() {
        Long userId = createUser(UserStatus.ACTIVE);
        authenticate(userId);

        userService.withdrawal("Test1234!");

        String currentSagaId = userRepository.findById(userId)
                .orElseThrow()
                .getWithdrawalSagaId();

        var event = new WithdrawalCompletedEvent(
                UUID.randomUUID().toString(),
                "2026-11-03T17:20:00",
                UUID.randomUUID().toString(),
                userId,
                List.of(),
                List.of()
        );

        assertThat(completionService.complete(event)).isFalse();

        var user = userRepository.findById(userId).orElseThrow();

        assertThat(user.getStatus())
                .isEqualTo(UserStatus.WITHDRAWAL_PENDING);
        assertThat(user.getWithdrawalSagaId()).isEqualTo(currentSagaId);
        assertThat(user.getWithdrawnAt()).isNull();
    }

    @Test
    void duplicateCompletionDoesNotChangeWithdrawalTime() {
        Long userId = createUser(UserStatus.ACTIVE);
        authenticate(userId);

        userService.withdrawal("Test1234!");

        String sagaId = userRepository.findById(userId)
                .orElseThrow()
                .getWithdrawalSagaId();

        var event = new WithdrawalCompletedEvent(
                UUID.randomUUID().toString(),
                "2026-11-03T17:20:00",
                sagaId,
                userId,
                List.of(),
                List.of()
        );

        assertThat(completionService.complete(event)).isTrue();

        var firstWithdrawalTime = userRepository.findById(userId)
                .orElseThrow()
                .getWithdrawnAt();

        assertThat(completionService.complete(event)).isFalse();

        var user = userRepository.findById(userId).orElseThrow();

        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(user.getWithdrawalSagaId()).isNull();
        assertThat(user.getWithdrawnAt()).isEqualTo(firstWithdrawalTime);
    }

    private Long createUser(UserStatus status) {
        return userRepository.saveAndFlush(
                UserEntity.builder()
                        .email(UUID.randomUUID() + "@test.com")
                        .password(passwordEncoder.encode("Test1234!"))
                        .gender(Gender.MALE)
                        .status(status)
                        .build()
        ).getId();
    }

    private void authenticate(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of()
                )
        );
    }
}