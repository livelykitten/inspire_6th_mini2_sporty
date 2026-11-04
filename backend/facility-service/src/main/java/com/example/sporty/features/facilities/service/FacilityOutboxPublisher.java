package com.example.sporty.features.facilities.service;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.sporty.features.facilities.domain.entity.FacilityOutbox;
import com.example.sporty.features.facilities.repository.FacilityOutboxRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FacilityOutboxPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(FacilityOutboxPublisher.class);
    private static final String PENDING = "PENDING";
    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final FacilityOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;


    @Scheduled(fixedRate = 1000)
    public void publishPending() {
        List<FacilityOutbox> pendingRows =
                outboxRepository.findByStatusOrderByIdAsc("PENDING");

        int sentCount = 0;
        for (FacilityOutbox outbox : pendingRows) {
            boolean sent = send(outbox);
            if (!sent) {
                break;
            }
            sentCount++;
        }

        if (sentCount > 0) {
            log.info(
                    "[KAFKA] 발행 완료: {}건, 남은 PENDING {}건",
                    sentCount,
                    outboxRepository.countByStatus(PENDING)
            );
        }
    }

    private boolean send(FacilityOutbox outbox) {
        try {
            kafkaTemplate.send(
                    outbox.getTopic(),
                    outbox.getMessageKey(),
                    outbox.getPayload()
            ).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            outbox.markSent();
            outboxRepository.save(outbox);
            return true;
        } catch (Exception exception) {
            outbox.markFailed();
            outboxRepository.save(outbox);
            log.warn(
                    "[KAFKA] 발행 실패: eventId={}, attemptCount={}",
                    outbox.getEventId(),
                    outbox.getAttemptCount(),
                    exception
            );
            return false;
        }
    }
}