package com.fintech.payments.service;

import com.fintech.payments.exception.*;
import com.fintech.payments.kafka.PaymentEventProducer;
import com.fintech.payments.model.*;
import com.fintech.payments.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final FraudDetectionService fraudDetectionService;
    private final PaymentEventProducer eventProducer;
    private final IdempotencyService idempotencyService;

    /**
     * Process a payment transaction with idempotency, fraud detection, and ACID guarantees.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public PaymentResponse processPayment(PaymentRequest request) {

        // 1. Idempotency check — prevents duplicate transactions
        String idempotencyKey = request.getIdempotencyKey();
        if (idempotencyService.isDuplicate(idempotencyKey)) {
            log.warn("Duplicate transaction attempt: {}", idempotencyKey);
            PaymentResponse cached = idempotencyService.getResult(idempotencyKey);
            return cached != null ? cached : PaymentResponse.failure("Duplicate request");
        }

        // 2. Load and validate accounts
        Account sourceAccount = accountService.getAccount(request.getSourceAccountId());
        Account destinationAccount = accountService.getAccount(request.getDestinationAccountId());
        validateAccounts(sourceAccount, destinationAccount);

        // 3. Fraud detection
        FraudScore fraudScore = fraudDetectionService.evaluateTransaction(
                request, sourceAccount, destinationAccount);

        if (fraudScore.isHighRisk()) {
            log.warn("Fraud detected for idempotency key {}: score={}", idempotencyKey, fraudScore.getScore());
            throw new FraudDetectedException(fraudScore);
        }

        // 4. Check sufficient funds
        BigDecimal amount = request.getAmount();
        if (sourceAccount.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(sourceAccount.getId(), amount);
        }

        // 5. Build transaction record
        Transaction transaction = Transaction.builder()
                .id(UUID.randomUUID().toString())
                .sourceAccountId(sourceAccount.getId())
                .destinationAccountId(destinationAccount.getId())
                .amount(amount)
                .currency(request.getCurrency())
                .status(TransactionStatus.PENDING)
                .fraudScore(BigDecimal.valueOf(fraudScore.getScore()))
                .idempotencyKey(idempotencyKey)
                .createdAt(LocalDateTime.now())
                .build();

        // 6. Double-entry accounting (atomic)
        try {
            // Save PENDING transaction first so FK on account_ledger is satisfied
            transactionRepository.save(transaction);

            accountService.debit(sourceAccount.getId(), amount, transaction.getId());
            accountService.credit(destinationAccount.getId(), amount, transaction.getId());

            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setCompletedAt(LocalDateTime.now());
            transactionRepository.save(transaction);

            // 7. Publish event for downstream consumers
            eventProducer.publishPaymentCompleted(transaction);

            log.info("Payment processed: {} | amount={} {}", transaction.getId(), amount, request.getCurrency());

            PaymentResponse response = PaymentResponse.success(transaction);
            idempotencyService.saveResult(idempotencyKey, response);
            return response;

        } catch (FraudDetectedException | InsufficientFundsException | InvalidAccountException e) {
            throw e; // re-throw business exceptions without wrapping
        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setErrorMessage(e.getMessage());
            transactionRepository.save(transaction);
            log.error("Payment failed for transaction {}: {}", transaction.getId(), e.getMessage(), e);
            throw new PaymentProcessingException(transaction.getId(), e);
        }
    }

    public Transaction getTransaction(String transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new InvalidAccountException("Transaction not found: " + transactionId));
    }

    public List<Transaction> getTransactionsByAccount(String accountId) {
        return transactionRepository.findBySourceAccountIdOrderByCreatedAtDesc(accountId);
    }

    private void validateAccounts(Account source, Account destination) {
        if (!source.isActive()) {
            throw new InvalidAccountException("Source account is not active: " + source.getId());
        }
        if (!destination.isActive()) {
            throw new InvalidAccountException("Destination account is not active: " + destination.getId());
        }
        if (source.getId().equals(destination.getId())) {
            throw new InvalidAccountException("Cannot transfer to the same account");
        }
    }
}
