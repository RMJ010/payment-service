package com.fintech.payments.controller;

import com.fintech.payments.model.*;
import com.fintech.payments.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Process a new payment transaction.
     * Requires idempotency-key header to prevent duplicate processing.
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(
            @Valid @RequestBody PaymentRequest request) {

        log.info("Processing payment request: idempotencyKey={}, amount={} {}",
                request.getIdempotencyKey(), request.getAmount(), request.getCurrency());

        PaymentResponse response = paymentService.processPayment(request);

        HttpStatus status = response.isSuccess() ? HttpStatus.CREATED : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Get a transaction by ID.
     */
    @GetMapping("/{transactionId}")
    public ResponseEntity<Transaction> getTransaction(@PathVariable String transactionId) {
        Transaction transaction = paymentService.getTransaction(transactionId);
        return ResponseEntity.ok(transaction);
    }

    /**
     * Get transaction history for an account.
     */
    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<Transaction>> getAccountTransactions(
            @PathVariable String accountId) {
        List<Transaction> transactions = paymentService.getTransactionsByAccount(accountId);
        return ResponseEntity.ok(transactions);
    }
}
