package com.example.sporty.features.exerciseMatching.withdrawal;

import org.springframework.transaction.support.TransactionSynchronization;

/** Per invocation, not shared across retries. Only the real transaction callback supplies proof. */
final class WithdrawalAttempt implements TransactionSynchronization {
    private int completion = STATUS_UNKNOWN;
    private boolean bodyFailed;

    void bodyFailed() { bodyFailed = true; }

    @Override
    public void afterCompletion(int status) { completion = status; }

    boolean rollbackConfirmed() { return bodyFailed && completion == STATUS_ROLLED_BACK; }
}
