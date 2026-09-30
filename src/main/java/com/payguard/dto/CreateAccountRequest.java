package com.payguard.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotBlank String holderName,
        @NotNull @DecimalMin(value = "0.00", message = "opening balance cannot be negative") BigDecimal openingBalance
) {
}
