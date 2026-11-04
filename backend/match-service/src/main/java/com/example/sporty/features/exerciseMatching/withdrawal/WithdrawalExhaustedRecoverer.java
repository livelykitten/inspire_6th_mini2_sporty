package com.example.sporty.features.exerciseMatching.withdrawal;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.sporty.features.exerciseMatching.client.UserServiceClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class WithdrawalExhaustedRecoverer implements ConsumerRecordRecoverer {
    private final ObjectMapper mapper;
    private final UserServiceClient users;
    private final WithdrawalFailureService failures;
    private final WithdrawalDltPublisher dlt;

    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception exception) {
        try {
            var event = mapper.readValue((String) record.value(), WithdrawalRequestedEvent.class);
            event.validate();
            if (WithdrawalRolledBackException.provesRollback(exception, event)) {
                // Feign is outside the compensation transaction as well. An outage is NOT cleanup failure.
                var status = users.getUserStatus(event.userId());
                if (status == null || !event.userId().equals(status.userId())
                        || !"WITHDRAWAL_PENDING".equals(status.status()) || !event.sagaId().equals(status.sagaId())) {
                    throw new IllegalStateException("Current withdrawal Saga cannot be verified");
                }
            }
            // Unknown outcomes can only resolve to an already persisted result; never create EV-04.
            failures.recordFailure(event, exception); // Separate proxy; returns only after commit.
            log.info("[KAFKA] withdrawal recovery committed eventId={} userId={} sagaId={}",
                    event.eventId(), event.userId(), event.sagaId());
            return;
        } catch (Exception recoveryFailure) {
            log.warn("[KAFKA] withdrawal recovery unsafe; preserve original topic={} partition={} offset={}",
                    record.topic(), record.partition(), record.offset(), recoveryFailure);
            // A failed/uncertain compensation commit also comes here. No second compensation attempt.
            dlt.publish(record, recoveryFailure); // Must throw if Kafka acknowledgment is not confirmed.
        }
    }
}
