package com.fintech.payments.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private String transactionId;
    private String status;
    private BigDecimal amount;
    private String currency;
    private String sourceAccountId;
    private String destinationAccountId;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String message;
    private boolean success;

    public static PaymentResponse success(Transaction transaction) {
        return PaymentResponse.builder()
                .transactionId(transaction.getId())
                .status(transaction.getStatus().name())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .sourceAccountId(transaction.getSourceAccountId())
                .destinationAccountId(transaction.getDestinationAccountId())
                .createdAt(transaction.getCreatedAt())
                .completedAt(transaction.getCompletedAt())
                .message("Payment processed successfully")
                .success(true)
                .build();
    }

    public static PaymentResponse failure(String message) {
        return PaymentResponse.builder()
                .status("FAILED")
                .message(message)
                .success(false)
                .build();
    }
}
