package com.example.sporty.features.exerciseMatching.withdrawal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.example.sporty.features.exerciseMatching.client.UserServiceClient;
import com.example.sporty.features.exerciseMatching.domain.dto.UserStatusResponseDto;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.transaction.support.TransactionSynchronization;

class WithdrawalRecoveryTest {
    final com.fasterxml.jackson.databind.ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    final UserServiceClient users = mock(UserServiceClient.class);
    final WithdrawalFailureService failures = mock(WithdrawalFailureService.class);
    final WithdrawalDltPublisher dlt = mock(WithdrawalDltPublisher.class);
    final WithdrawalExhaustedRecoverer recoverer = new WithdrawalExhaustedRecoverer(mapper, users, failures, dlt);
    final WithdrawalRequestedEvent event = new WithdrawalRequestedEvent(UUID.randomUUID().toString(),
            LocalDateTime.now().withNano(0), UUID.randomUUID().toString(), 7L);

    ConsumerRecord<String, String> record() throws Exception {
        return new ConsumerRecord<>(WithdrawalRequestedEvent.TOPIC, 0, 12L, "7", mapper.writeValueAsString(event));
    }

    @Test
    void onlyBodyFailureWithConfirmedRollbackSuppliesProof() {
        var attempt = new WithdrawalAttempt();
        attempt.bodyFailed();
        assertThat(attempt.rollbackConfirmed()).isFalse();
        attempt.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);
        assertThat(attempt.rollbackConfirmed()).isFalse();
        attempt.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(attempt.rollbackConfirmed()).isFalse();
        attempt.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(attempt.rollbackConfirmed()).isTrue();
        var commitFailure = new WithdrawalAttempt();
        commitFailure.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(commitFailure.rollbackConfirmed()).isFalse();
    }

    @Test
    void rollbackProofCannotBeReusedForAnotherEvent() {
        var other = new WithdrawalRequestedEvent(UUID.randomUUID().toString(), event.occurredAt(), event.sagaId(), 7L);
        var failure = new WithdrawalRolledBackException(event, new IllegalStateException("failed"));
        assertThat(WithdrawalRolledBackException.provesRollback(new IllegalStateException(failure), event)).isTrue();
        assertThat(WithdrawalRolledBackException.provesRollback(failure, other)).isFalse();
    }

    @Test
    void confirmedCleanupFailureRevalidatesFeignOutsideCompensation() throws Exception {
        when(users.getUserStatus(7L)).thenReturn(new UserStatusResponseDto(7L, "WITHDRAWAL_PENDING", event.sagaId()));
        var failure = new WithdrawalRolledBackException(event, new IllegalStateException("failed"));
        recoverer.accept(record(), new IllegalStateException(failure));
        var order = inOrder(users, failures);
        order.verify(users).getUserStatus(7L);
        order.verify(failures).recordFailure(eq(event), any());
        verifyNoInteractions(dlt);
    }

    @Test
    void failedRecoveryLookupCannotCompensate() throws Exception {
        when(users.getUserStatus(7L)).thenThrow(new IllegalStateException("Feign unavailable"));
        recoverer.accept(record(), new WithdrawalRolledBackException(event, new IllegalStateException("failed")));
        verifyNoInteractions(failures);
        verify(dlt).publish(any(), any());
    }

    @Test
    void staleSagaCannotCompensate() throws Exception {
        when(users.getUserStatus(7L)).thenReturn(new UserStatusResponseDto(7L, "WITHDRAWAL_PENDING", "old"));
        recoverer.accept(record(), new WithdrawalRolledBackException(event, new IllegalStateException("failed")));
        verifyNoInteractions(failures);
        verify(dlt).publish(any(), any());
    }

    @Test
    void uncertainCompensationCommitIsPreservedInDlt() throws Exception {
        doThrow(new IllegalStateException("commit acknowledgment lost")).when(failures).recordFailure(any(), any());
        var original = record();
        recoverer.accept(original, new WithdrawalCommitUncertainException(new IllegalStateException("unknown")));
        verifyNoInteractions(users);
        verify(dlt).publish(eq(original), any());
    }

    @Test
    void dltFailureEscapesRecovererSoOriginalCannotBeCompleted() throws Exception {
        doThrow(new IllegalStateException("cannot compensate")).when(failures).recordFailure(any(), any());
        doThrow(new org.springframework.kafka.KafkaException("broker down")).when(dlt).publish(any(), any());
        var original = record();
        assertThatThrownBy(() -> recoverer.accept(original, new IllegalStateException("lookup failed")))
                .isInstanceOf(org.springframework.kafka.KafkaException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void realErrorHandlerCommitsOnlyAfterDltSuccess() throws Exception {
        doThrow(new IllegalStateException("cannot compensate")).when(failures).recordFailure(any(), any());
        doThrow(new org.springframework.kafka.KafkaException("DLT offline")).when(dlt).publish(any(), any());
        var handler = new WithdrawalKafkaConfiguration().withdrawalErrorHandler(recoverer);
        org.apache.kafka.clients.consumer.Consumer<String, String> consumer = mock(org.apache.kafka.clients.consumer.Consumer.class);
        var container = mock(MessageListenerContainer.class);
        var props = new ContainerProperties(WithdrawalRequestedEvent.TOPIC);
        props.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        props.setSyncCommits(true);
        props.setSyncCommitTimeout(java.time.Duration.ofSeconds(5));
        when(container.getContainerProperties()).thenReturn(props);
        var original = record();
        var error = new WithdrawalCommitUncertainException(new IllegalStateException("unknown"));
        assertThatThrownBy(() -> handler.handleRemaining(error, List.of(original), consumer, container))
                .isInstanceOf(RuntimeException.class).hasMessage("Record in retry and not yet recovered");
        verify(consumer, never()).commitSync(anyMap(), any(java.time.Duration.class));
        doNothing().when(dlt).publish(any(), any());
        handler.handleRemaining(error, List.of(original), consumer, container);
        var order = inOrder(dlt, consumer);
        order.verify(dlt, times(2)).publish(eq(original), any());
        order.verify(consumer).commitSync(anyMap(), any(java.time.Duration.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void dltPublisherPreservesPayloadKeyHeadersAndRequiresBrokerAck() throws Exception {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        var publisher = new WithdrawalDltPublisher(kafka);
        var original = record();
        original.headers().add("test-header", new byte[]{1});
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")))
                .thenReturn(CompletableFuture.completedFuture(null));
        assertThatThrownBy(() -> publisher.publish(original, new IllegalStateException("failed")))
                .isInstanceOf(org.springframework.kafka.KafkaException.class);
        publisher.publish(original, new IllegalStateException("failed"));
        var captor = org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka, times(2)).send(captor.capture());
        for (var sent : captor.getAllValues()) {
            assertThat(sent.topic()).isEqualTo(WithdrawalRequestedEvent.TOPIC + ".DLT");
            assertThat(sent.partition()).isEqualTo(original.partition());
            assertThat(sent.key()).isEqualTo(original.key());
            assertThat(sent.value()).isEqualTo(original.value());
            assertThat(sent.headers().lastHeader("test-header").value()).containsExactly((byte) 1);
            assertThat(new String(sent.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC).value(),
                    java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(original.topic());
        }
    }
}
