package com.example.sporty.features.users.scheduler;

import com.example.sporty.features.users.service.UserOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.user-outbox.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class UserOutboxPublisher {

    private final UserOutboxService outboxService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(
            fixedDelayString = "${app.user-outbox.fixed-delay-ms:1000}"
    )
    public void publishPendingEvents() {
        try {
            for (var event : outboxService.findPendingEvents()) {
                if (!publishOne(event)) {
                    // 실패한 이벤트를 다음 주기에 먼저 재시도한다.
                    break;
                }
            }
        } catch (Exception exception) {
            log.error("[KAFKA] Outbox 조회 또는 처리 실패", exception);
        }
    }

    private boolean publishOne(UserOutboxService.PendingEvent event) {
        try {
            kafkaTemplate.send(
                    event.topic(),
                    event.messageKey(),
                    event.payload()
            ).get(15, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn(
                    "[KAFKA] 발행 대기 중단 eventId={}",
                    event.eventId()
            );
            return false;
        } catch (ExecutionException | TimeoutException exception) {
            recordPublishFailure(event, exception);
            return false;
        } catch (RuntimeException exception) {
            recordPublishFailure(event, exception);
            return false;
        }

        // Kafka 전송 성공과 DB의 SENT 저장은 별개 작업이다.
        try {
            outboxService.markSent(event.id());
            log.info(
                    "[KAFKA] 발행 완료 topic={} eventId={} key={}",
                    event.topic(),
                    event.eventId(),
                    event.messageKey()
            );
            return true;
        } catch (Exception exception) {
            log.error(
                    "[KAFKA] 전송 확인 후 SENT 저장 실패 "
                            + "eventId={} 재전송 가능",
                    event.eventId(),
                    exception
            );
            return false;
        }
    }

    private void recordPublishFailure(
            UserOutboxService.PendingEvent event,
            Exception exception
    ) {
        log.warn(
                "[KAFKA] 발행 실패 또는 결과 미확인 "
                        + "eventId={} PENDING 유지",
                event.eventId(),
                exception
        );

        try {
            outboxService.recordFailure(event.id());
        } catch (Exception databaseException) {
            log.error(
                    "[KAFKA] 발행 실패 횟수 저장 실패 eventId={}",
                    event.eventId(),
                    databaseException
            );
        }
    }
}