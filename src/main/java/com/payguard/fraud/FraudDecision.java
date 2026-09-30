package com.payguard.fraud;

import com.payguard.model.TransactionStatus;

/** The outcome of one fraud rule (or of the whole engine). */
public record FraudDecision(TransactionStatus status, String reason) {

    public static FraudDecision approve() {
        return new FraudDecision(TransactionStatus.APPROVED, null);
    }

    public static FraudDecision flag(String reason) {
        return new FraudDecision(TransactionStatus.FLAGGED, reason);
    }

    public static FraudDecision decline(String reason) {
        return new FraudDecision(TransactionStatus.DECLINED, reason);
    }

    /** DECLINED beats FLAGGED beats APPROVED. */
    int severity() {
        return switch (status) {
            case APPROVED -> 0;
            case FLAGGED -> 1;
            case DECLINED -> 2;
        };
    }
}
