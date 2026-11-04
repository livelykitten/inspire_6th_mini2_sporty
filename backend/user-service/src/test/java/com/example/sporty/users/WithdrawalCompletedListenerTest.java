package com.example.sporty.users;

import com.example.sporty.features.auth.service.RefreshTokenService;
import com.example.sporty.features.users.domain.dto.WithdrawalCompletedEvent;
import com.example.sporty.features.users.listener.WithdrawalCompletedListener;
import com.example.sporty.features.users.service.WithdrawalCompletionService;
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
class WithdrawalCompletedListenerTest {

    @Mock
    WithdrawalCompletionService completionService;

    @Mock
    RefreshTokenService refreshTokenService;

    WithdrawalCompletedListener listener;

    private final String payload = """
            {
              "eventId": "11111111-1111-4111-8111-111111111111",
              "occurredAt": "2026-11-03T17:20:00",
              "sagaId": "22222222-2222-4222-8222-222222222222",
              "userId": 4,
              "deletedMatchIds": [],
              "leftMatchIds": []
            }
            """;

    @BeforeEach
    void setUp() {
        listener = new WithdrawalCompletedListener(
                new ObjectMapper(),
                completionService,
                refreshTokenService
        );
    }

    @Test
    void completionRunsBeforeTokenDeletion() throws Exception {
        when(completionService.complete(any(WithdrawalCompletedEvent.class)))
                .thenReturn(true);

        listener.receive(payload);

        var order = inOrder(completionService, refreshTokenService);
        order.verify(completionService)
                .complete(any(WithdrawalCompletedEvent.class));
        order.verify(refreshTokenService).delete(4L);

        verify(completionService, never()).isWithdrawn(anyLong());
    }

    @Test
    void ignoredEventForNonWithdrawnUserDoesNotDeleteToken()
            throws Exception {
        when(completionService.complete(any(WithdrawalCompletedEvent.class)))
                .thenReturn(false);
        when(completionService.isWithdrawn(4L)).thenReturn(false);

        listener.receive(payload);

        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void duplicateEventForWithdrawnUserRetriesTokenCleanup()
            throws Exception {
        when(completionService.complete(any(WithdrawalCompletedEvent.class)))
                .thenReturn(false);
        when(completionService.isWithdrawn(4L)).thenReturn(true);

        listener.receive(payload);

        verify(refreshTokenService).delete(4L);
    }

    @Test
    void redisFailureIsPropagatedAndNextDeliveryRetriesDeletion()
            throws Exception {
        when(completionService.complete(any(WithdrawalCompletedEvent.class)))
                .thenReturn(true, false);
        when(completionService.isWithdrawn(4L)).thenReturn(true);

        doThrow(new IllegalStateException("테스트용 Redis 장애"))
                .doNothing()
                .when(refreshTokenService).delete(4L);

        assertThatThrownBy(() -> listener.receive(payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("테스트용 Redis 장애");

        listener.receive(payload);

        verify(refreshTokenService, times(2)).delete(4L);
    }

    @Test
    void databaseFailureDoesNotDeleteToken() {
        when(completionService.complete(any(WithdrawalCompletedEvent.class)))
                .thenThrow(new IllegalStateException("테스트용 DB 장애"));

        assertThatThrownBy(() -> listener.receive(payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("테스트용 DB 장애");

        verifyNoInteractions(refreshTokenService);
    }
}