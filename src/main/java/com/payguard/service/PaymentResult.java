package com.payguard.service;

import com.payguard.dto.TransactionResponse;

/**
 * @param replayed true when this response came from an earlier request with the same
 *                 Idempotency-Key — nothing was charged this time.
 */
public record PaymentResult(TransactionResponse transaction, boolean replayed) {
}
