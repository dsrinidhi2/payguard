package com.payguard.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PaymentRequest(
        @NotNull Long fromAccountId,
        @NotNull Long toAccountId,
        @NotNull @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
        @Digits(integer = 17, fraction = 2, message = "amount can have at most 2 decimal places")
        BigDecimal amount
) {
    /** Stable string used to detect the same Idempotency-Key being reused for a different payment. */
    public String fingerprint() {
        return fromAccountId + "|" + toAccountId + "|" + amount.stripTrailingZeros().toPlainString();
    }
}
