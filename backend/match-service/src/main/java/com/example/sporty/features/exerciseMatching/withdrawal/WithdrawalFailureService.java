package com.example.sporty.features.exerciseMatching.withdrawal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** W-32 skeleton. Must never return normally until compensation has actually committed. */
@Service
public class WithdrawalFailureService {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(WithdrawalRequestedEvent event, Throwable cause) {
        // TODO W-32: verify rollback/unknown commit; lock guard; recheck processed_event and
        // result outbox; atomically insert processed_event + EV-04. Do not unblock on speculation.
        throw new UnsupportedOperationException("W-32 compensation recording is not implemented", cause);
    }
}
