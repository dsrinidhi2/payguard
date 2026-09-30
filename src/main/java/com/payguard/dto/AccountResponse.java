package com.payguard.dto;

import com.payguard.model.Account;

import java.math.BigDecimal;

public record AccountResponse(Long id, String holderName, BigDecimal balance) {
    public static AccountResponse from(Account a) {
        return new AccountResponse(a.getId(), a.getHolderName(), a.getBalance());
    }
}
