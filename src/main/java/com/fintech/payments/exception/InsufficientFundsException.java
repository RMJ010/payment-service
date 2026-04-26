package com.fintech.payments.exception;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {
    private final String accountId;
    private final BigDecimal requiredAmount;

    public InsufficientFundsException(String accountId, BigDecimal requiredAmount) {
        super(String.format("Insufficient funds in account %s. Required: %s", accountId, requiredAmount));
        this.accountId = accountId;
        this.requiredAmount = requiredAmount;
    }

    public String getAccountId() { return accountId; }
    public BigDecimal getRequiredAmount() { return requiredAmount; }
}
