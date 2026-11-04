package com.example.sporty.features.exerciseMatching.withdrawal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import java.util.UUID;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

class WithdrawalListenerTest {
    final com.fasterxml.jackson.databind.ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void acknowledgesOnlyAfterServiceReturns() throws Exception {
        var service = mock(WithdrawalService.class);
        var ack = mock(Acknowledgment.class);
        var event = event();
        new WithdrawalRequestedListener(mapper, service).consume(mapper.writeValueAsString(event), ack);
        var order = inOrder(service, ack);
        order.verify(service).handle(event);
        order.verify(ack).acknowledge();
    }

    @Test
    void cleanupFailureDoesNotAcknowledge() throws Exception {
        var service = mock(WithdrawalService.class);
        var ack = mock(Acknowledgment.class);
        var event = event();
        doThrow(new IllegalStateException("rollback")).when(service).handle(event);
        var listener = new WithdrawalRequestedListener(mapper, service);
        assertThatThrownBy(() -> listener.consume(mapper.writeValueAsString(event), ack))
                .hasMessage("rollback");
        verifyNoInteractions(ack);
    }

    @Test
    void malformedPayloadDoesNotAcknowledge() {
        var service = mock(WithdrawalService.class);
        var ack = mock(Acknowledgment.class);
        assertThatThrownBy(() -> new WithdrawalRequestedListener(mapper, service).consume("{", ack))
                .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        verifyNoInteractions(service, ack);
    }

    @Test
    void dltFailureMustThrowAndNeverSilentlyDropRecord() {
        var record = new ConsumerRecord<String, String>(WithdrawalRequestedEvent.TOPIC, 0, 0L, "7", "{}");
        var recoverer = new WithdrawalExhaustedRecoverer(mapper,
                mock(com.example.sporty.features.exerciseMatching.client.UserServiceClient.class),
                mock(WithdrawalFailureService.class),
                new WithdrawalDltPublisher(mock(org.springframework.kafka.core.KafkaTemplate.class)));
        assertThatThrownBy(() -> recoverer.accept(record, new IllegalStateException("failed")))
                .isInstanceOf(org.springframework.kafka.KafkaException.class).hasMessageContaining("retain original offset");
    }

    @Test
    void failureInjectionIsDisabledByDefaultAndThrowsWhenEnabled() {
        new WithdrawalCleanupHook(false).afterCleanup(event());
        assertThatThrownBy(() -> new WithdrawalCleanupHook(true).afterCleanup(event()))
                .hasMessage("Injected withdrawal cleanup failure");
    }

    WithdrawalRequestedEvent event() {
        return new WithdrawalRequestedEvent(UUID.randomUUID().toString(), LocalDateTime.now().withNano(0),
                UUID.randomUUID().toString(), 7L);
    }
}
