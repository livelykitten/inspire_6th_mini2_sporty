package com.example.sporty.features.exerciseMatching.withdrawal;

import java.util.List;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class WithdrawalOutcomeService {
    public enum Outcome { NONE, COMPLETED, FAILED }
    private final MatchOutboxRepository outbox;
    private final ObjectMapper mapper;

    /** Caller must already own the user's Guard. Locking read avoids an old RR snapshot. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome find(WithdrawalRequestedEvent event) {
        Outcome found = Outcome.NONE;
        for (var row : outbox.findOutcomes(event.userId().toString(),
                List.of(WithdrawalCompletedEvent.TOPIC, WithdrawalFailedEvent.TOPIC))) {
            try {
                var body = mapper.readTree(row.getPayload());
                if (body == null || !body.path("userId").canConvertToLong()
                        || body.path("userId").longValue() != event.userId()
                        || !body.path("sagaId").isTextual()) {
                    throw new IllegalStateException("Invalid persisted withdrawal outcome");
                }
                if (!event.sagaId().equals(body.path("sagaId").textValue())) continue;
                var candidate = WithdrawalCompletedEvent.TOPIC.equals(row.getTopic()) ? Outcome.COMPLETED : Outcome.FAILED;
                if (found != Outcome.NONE && found != candidate) {
                    throw new IllegalStateException("Conflicting persisted withdrawal outcomes");
                }
                found = candidate;
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Unreadable persisted withdrawal outcome", e);
            }
        }
        return found;
    }
}
