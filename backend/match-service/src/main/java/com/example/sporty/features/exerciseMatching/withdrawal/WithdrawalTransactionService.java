package com.example.sporty.features.exerciseMatching.withdrawal;

import java.time.*;
import java.util.UUID;
import com.example.sporty.features.exerciseMatching.service.MatchService;
import com.example.sporty.features.exerciseMatching.repository.ProcessedEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WithdrawalTransactionService {
    private final MatchUserGuardService guards;
    private final ProcessedEventRepository processed;
    private final MatchService matches;
    private final MatchOutboxRepository outbox;
    private final ObjectMapper mapper;
    private final WithdrawalCleanupHook hook;

    @Transactional
    public boolean complete(WithdrawalRequestedEvent event) {
        var guard = guards.lock(event.userId());
        if (processed.lock(event.eventId()).isPresent() || guard.isBlocked()) return false;
        var result = matches.removeMatchesForWithdrawal(event.userId());
        hook.afterCleanup(event);
        guard.block();
        var completed = new WithdrawalCompletedEvent(UUID.randomUUID().toString(),
                LocalDateTime.now(ZoneId.of("Asia/Seoul")).withNano(0), event.sagaId(), event.userId(),
                result.deletedMatchIds(), result.leftMatchIds());
        try {
            outbox.save(new MatchOutbox(completed.eventId(), WithdrawalCompletedEvent.TOPIC,
                    event.userId(), mapper.writeValueAsString(completed)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize EV-03", e);
        }
        // Force INSERT, do not merge a potentially conflicting event marker.
        processed.insert(event.eventId(), WithdrawalRequestedEvent.TOPIC);
        return true;
    }
}
