package com.example.sporty.features.exerciseMatching.withdrawal;

import org.springframework.stereotype.Component;

/** Extension point inside the cleanup transaction. W-32 adds configurable failure injection. */
@Component
public class WithdrawalCleanupHook {
    public void afterCleanup(WithdrawalRequestedEvent event) {
        // Normal W-22 processing intentionally does nothing here.
    }
}
