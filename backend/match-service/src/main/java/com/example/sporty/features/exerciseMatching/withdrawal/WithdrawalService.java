package com.example.sporty.features.exerciseMatching.withdrawal;

import com.example.sporty.features.exerciseMatching.client.UserServiceClient;
import com.example.sporty.features.exerciseMatching.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawalService {
    private final UserServiceClient users;
    private final ProcessedEventRepository processed;
    private final WithdrawalTransactionService transactions;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void handle(WithdrawalRequestedEvent event) {
        event.validate();
        if (processed.existsById(event.eventId())) {
            log.info("[KAFKA] duplicate withdrawal ignored eventId={} userId={}", event.eventId(), event.userId());
            return;
        }
        // Never hold a match DB transaction/guard lock while waiting on Feign.
        var status = users.getUserStatus(event.userId());
        if (status == null || status.status() == null || !event.userId().equals(status.userId())) {
            throw new IllegalStateException("Invalid IN-01 response for withdrawal");
        }
        if (!"WITHDRAWAL_PENDING".equals(status.status()) || !event.sagaId().equals(status.sagaId())) {
            log.info("[KAFKA] stale withdrawal ignored eventId={} userId={} sagaId={}",
                    event.eventId(), event.userId(), event.sagaId());
            return;
        }
        boolean applied = transactions.complete(event); // Proxy returns only after DB commit.
        log.info("[KAFKA] withdrawal {} eventId={} userId={} sagaId={}",
                applied ? "completed" : "duplicate ignored", event.eventId(), event.userId(), event.sagaId());
    }
}
