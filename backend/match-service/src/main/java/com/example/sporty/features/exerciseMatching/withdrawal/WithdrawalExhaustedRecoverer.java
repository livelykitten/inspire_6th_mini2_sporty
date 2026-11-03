package com.example.sporty.features.exerciseMatching.withdrawal;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/** Deliberately fail closed until W-32 recovery and confirmed DLT publishing are implemented. */
@Slf4j
@Component
public class WithdrawalExhaustedRecoverer implements ConsumerRecordRecoverer {
    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception exception) {
        log.error("[KAFKA] withdrawal retries exhausted; NOT recovered topic={} partition={} offset={}",
                record.topic(), record.partition(), record.offset(), exception);
        // TODO W-32: verified rollback -> WithdrawalFailureService; unknown commit ->
        // confirmed publish to user.withdrawal.requested.DLT. Return only after durable recovery.
        throw new IllegalStateException("W-32 recovery/DLT is not implemented; retain original offset", exception);
    }
}
