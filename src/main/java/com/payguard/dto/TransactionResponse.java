package com.payguard.dto;

import com.payguard.model.Transaction;
import com.payguard.model.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        Long transactionId,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        TransactionStatus status,
        String reason,
        Instant createdAt
) {
    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(t.getId(), t.getFromAccountId(), t.getToAccountId(),
                t.getAmount(), t.getStatus(), t.getReason(), t.getCreatedAt());
    }
}
