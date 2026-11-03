package com.example.sporty.features.exerciseMatching.withdrawal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** W-32 failure-injection seam, invoked inside the cleanup transaction. */
@Component
public class WithdrawalCleanupHook {
    private final boolean failCleanup;

    public WithdrawalCleanupHook(@Value("${saga.fail-cleanup:false}") boolean failCleanup) {
        this.failCleanup = failCleanup;
    }

    public void afterCleanup(WithdrawalRequestedEvent event) {
        if (failCleanup) throw new IllegalStateException("Injected withdrawal cleanup failure");
    }
}
