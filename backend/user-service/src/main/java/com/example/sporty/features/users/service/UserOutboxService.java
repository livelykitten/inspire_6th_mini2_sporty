package com.example.sporty.features.users.service;

import com.example.sporty.features.users.repository.UserOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserOutboxService {

    private final UserOutboxRepository outboxRepository;

    public record PendingEvent(
            Long id,
            String eventId,
            String topic,
            String messageKey,
            String payload
    ) {
    }

    @Transactional(readOnly = true)
    public List<PendingEvent> findPendingEvents() {
        return outboxRepository.findTop100ByStatusOrderByIdAsc("PENDING")
                .stream()
                .map(outbox -> new PendingEvent(
                        outbox.getId(),
                        outbox.getEventId(),
                        outbox.getTopic(),
                        outbox.getMessageKey(),
                        outbox.getPayload()
                ))
                .toList();
    }

    @Transactional
    public void markSent(Long id) {
        int updated = outboxRepository.markSent(
                id,
                LocalDateTime.now(ZoneId.of("Asia/Seoul"))
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Outbox 전송 완료 상태를 저장하지 못했습니다. id=" + id
            );
        }
    }

    @Transactional
    public void recordFailure(Long id) {
        outboxRepository.recordFailure(id);
    }
}