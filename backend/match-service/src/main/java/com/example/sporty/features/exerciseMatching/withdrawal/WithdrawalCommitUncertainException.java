package com.example.sporty.features.exerciseMatching.withdrawal;

/** Never compensate based on this exception; inspect durable outcomes or preserve in DLT. */
public final class WithdrawalCommitUncertainException extends IllegalStateException {
    WithdrawalCommitUncertainException(Throwable cause) {
        super("Withdrawal transaction outcome is uncertain", cause);
    }
}
