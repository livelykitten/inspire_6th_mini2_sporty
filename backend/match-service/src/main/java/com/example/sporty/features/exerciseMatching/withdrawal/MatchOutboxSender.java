package com.example.sporty.features.exerciseMatching.withdrawal;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchOutboxSender {
    private final MatchOutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;

    @Transactional
    public boolean publishNext() {
        var pending = outbox.findFirstByStatusOrderByIdAsc("PENDING");
        if (pending.isEmpty()) return false;
        var row = pending.get();
        try {
            kafka.send(row.getTopic(), row.getMessageKey(), row.getPayload()).get(5, TimeUnit.SECONDS);
            row.sent();
            log.info("[KAFKA] outbox published eventId={} topic={} key={}",
                    row.getEventId(), row.getTopic(), row.getMessageKey());
            return true;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            row.failedAttempt();
            log.warn("[KAFKA] outbox retry pending eventId={} attempts={}", row.getEventId(), row.getAttemptCount(), e);
            return false; // Preserve id order; retry the same persisted payload on the next tick.
        }
    }
}
