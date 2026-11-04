package com.example.sporty.users;

import com.example.sporty.features.users.domain.dto.WithdrawalFailedEvent;
import com.example.sporty.features.users.listener.WithdrawalFailedListener;
import com.example.sporty.features.users.service.WithdrawalCompensationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WithdrawalFailedListenerTest {
    @Mock WithdrawalCompensationService compensation;
    WithdrawalFailedListener listener;

    final String payload = """
            {"eventId":"event-1","occurredAt":"2026-11-04T10:00:00",
             "sagaId":"saga-1","userId":4,"reason":"cleanup failed"}
            """;

    @BeforeEach
    void setUp() {
        listener = new WithdrawalFailedListener(new ObjectMapper(), compensation);
    }

    @Test
    void deserializesFailureAndDelegatesCompensation() throws Exception {
        listener.receive(payload);
        verify(compensation).compensate(new WithdrawalFailedEvent(
                "event-1", "2026-11-04T10:00:00", "saga-1", 4L, "cleanup failed"));
        verifyNoMoreInteractions(compensation);
    }

    @Test
    void duplicateOrStaleEventCompletesWithoutError() throws Exception {
        when(compensation.compensate(any())).thenReturn(false);
        listener.receive(payload);
        verify(compensation).compensate(any(WithdrawalFailedEvent.class));
    }

    @Test
    void databaseFailurePropagatesToKafkaErrorHandler() {
        when(compensation.compensate(any())).thenThrow(new IllegalStateException("DB unavailable"));
        assertThatThrownBy(() -> listener.receive(payload))
                .isInstanceOf(IllegalStateException.class).hasMessage("DB unavailable");
    }

    @Test
    void malformedJsonDoesNotInvokeCompensation() {
        assertThatThrownBy(() -> listener.receive("{broken"))
                .isInstanceOf(JsonProcessingException.class);
        verifyNoInteractions(compensation);
    }
}
