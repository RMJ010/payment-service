package com.fintech.payments.exception;

public class PaymentProcessingException extends RuntimeException {
    private final String transactionId;

    public PaymentProcessingException(String transactionId, Throwable cause) {
        super(String.format("Payment processing failed for transaction: %s", transactionId), cause);
        this.transactionId = transactionId;
    }

    public String getTransactionId() { return transactionId; }
}
