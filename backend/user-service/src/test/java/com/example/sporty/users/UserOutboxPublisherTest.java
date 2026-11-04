package com.example.sporty.users;

import com.example.sporty.features.users.scheduler.UserOutboxPublisher;
import com.example.sporty.features.users.service.UserOutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserOutboxPublisherTest {

    @Mock
    UserOutboxService outboxService;

    @Mock
    KafkaTemplate<String, String> kafkaTemplate;

    UserOutboxPublisher publisher;

    private final UserOutboxService.PendingEvent first =
            new UserOutboxService.PendingEvent(
                    1L,
                    "event-1",
                    "user.withdrawal.requested",
                    "2",
                    "{\"eventId\":\"event-1\",\"userId\":2}"
            );

    private final UserOutboxService.PendingEvent second =
            new UserOutboxService.PendingEvent(
                    2L,
                    "event-2",
                    "user.withdrawal.requested",
                    "3",
                    "{\"eventId\":\"event-2\",\"userId\":3}"
            );

    @BeforeEach
    void setUp() {
        publisher = new UserOutboxPublisher(
                outboxService,
                kafkaTemplate
        );
    }

    @Test
    void marksSentOnlyAfterKafkaAcknowledgement() {
        when(outboxService.findPendingEvents())
                .thenReturn(List.of(first));

        var acknowledgement =
                new CompletableFuture<SendResult<String, String>>();

        when(kafkaTemplate.send(
                first.topic(), first.messageKey(), first.payload()
        )).thenAnswer(invocation -> {
            verify(outboxService, never()).markSent(anyLong());
            acknowledgement.complete(null);
            return acknowledgement;
        });

        publisher.publishPendingEvents();

        var order = inOrder(kafkaTemplate, outboxService);
        order.verify(kafkaTemplate).send(
                first.topic(), first.messageKey(), first.payload()
        );
        order.verify(outboxService).markSent(first.id());

        verify(outboxService, never()).recordFailure(anyLong());
    }

    @Test
    void kafkaFailureRecordsFailureAndStopsCurrentBatch() {
        when(outboxService.findPendingEvents())
                .thenReturn(List.of(first, second));

        when(kafkaTemplate.send(
                first.topic(), first.messageKey(), first.payload()
        )).thenReturn(CompletableFuture.failedFuture(
                new RuntimeException("테스트용 전송 실패")
        ));

        publisher.publishPendingEvents();

        verify(outboxService).recordFailure(first.id());
        verify(outboxService, never()).markSent(anyLong());
        verify(kafkaTemplate, never()).send(
                second.topic(), second.messageKey(), second.payload()
        );
    }

    @Test
    void sentUpdateFailureDoesNotCountAsKafkaFailure() {
        when(outboxService.findPendingEvents())
                .thenReturn(List.of(first, second));

        when(kafkaTemplate.send(
                first.topic(), first.messageKey(), first.payload()
        )).thenReturn(CompletableFuture.completedFuture(null));

        doThrow(new IllegalStateException("테스트용 DB 저장 실패"))
                .when(outboxService).markSent(first.id());

        publisher.publishPendingEvents();

        verify(outboxService, never()).recordFailure(anyLong());
        verify(kafkaTemplate, never()).send(
                second.topic(), second.messageKey(), second.payload()
        );
    }

    @Test
    void retryUsesExactlyTheSameKeyAndPayload() {
        when(outboxService.findPendingEvents())
                .thenReturn(List.of(first));

        when(kafkaTemplate.send(
                first.topic(), first.messageKey(), first.payload()
        ))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException("테스트용 전송 실패")
                ))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishPendingEvents();
        publisher.publishPendingEvents();

        verify(kafkaTemplate, times(2)).send(
                first.topic(), first.messageKey(), first.payload()
        );
        verify(outboxService).recordFailure(first.id());
        verify(outboxService).markSent(first.id());
    }
}