package com.payguard.model;

public enum TransactionStatus {
    /** Passed all fraud rules, money moved. */
    APPROVED,
    /** Suspicious but not blocked outright: recorded, money NOT moved, held for manual review. */
    FLAGGED,
    /** Blocked by a fraud rule, money NOT moved. */
    DECLINED
}
