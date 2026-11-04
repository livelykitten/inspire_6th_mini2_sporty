package com.example.sporty.features.exerciseMatching.withdrawal;

import java.util.Collections;
import java.util.IdentityHashMap;

/** Created only after the cleanup proxy has returned an exception AND rollback was confirmed. */
public final class WithdrawalRolledBackException extends IllegalStateException {
    private final WithdrawalRequestedEvent event;

    WithdrawalRolledBackException(WithdrawalRequestedEvent event, Throwable cause) {
        super(cause.getMessage(), cause);
        this.event = event;
    }

    static boolean provesRollback(Throwable cause, WithdrawalRequestedEvent event) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        while (cause != null && seen.add(cause)) {
            if (cause instanceof WithdrawalRolledBackException confirmed && confirmed.event.equals(event)) return true;
            cause = cause.getCause();
        }
        return false;
    }
}
