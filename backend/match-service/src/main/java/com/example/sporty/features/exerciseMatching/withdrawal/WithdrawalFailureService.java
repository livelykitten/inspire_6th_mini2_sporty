package com.example.sporty.features.exerciseMatching.withdrawal;

import com.example.sporty.features.exerciseMatching.repository.ProcessedEventRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WithdrawalFailureService {
    private final MatchUserGuardService guards;
    private final ProcessedEventRepository processed;
    private final MatchOutboxRepository outbox;
    private final WithdrawalOutcomeService outcomes;
    private final ObjectMapper mapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(WithdrawalRequestedEvent event, Throwable cause) {
        event.validate();
        var guard = guards.lock(event.userId());
        boolean recorded = processed.lock(event.eventId()).isPresent();
        var outcome = outcomes.find(event);
        // Includes different EV-02 eventIds referring to the same user/Saga.
        if (outcome != WithdrawalOutcomeService.Outcome.NONE) return;
        if (recorded || guard.isBlocked() || !WithdrawalRolledBackException.provesRollback(cause, event)) {
            throw new IllegalStateException("Cannot safely compensate withdrawal", cause);
        }
        var failed = new WithdrawalFailedEvent(UUID.randomUUID().toString(),
                LocalDateTime.now(ZoneId.of("Asia/Seoul")).withNano(0), event.sagaId(), event.userId(),
                "MATCH_CLEANUP_FAILED"); // Stable reason, never expose a DB/Feign exception message.
        try {
            outbox.save(new MatchOutbox(failed.eventId(), WithdrawalFailedEvent.TOPIC,
                    event.userId(), mapper.writeValueAsString(failed)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize EV-04", e);
        }
        processed.insert(event.eventId(), WithdrawalRequestedEvent.TOPIC);
        // Preserve the rolled-back Guard value; no speculative unblock or block.
    }
}
