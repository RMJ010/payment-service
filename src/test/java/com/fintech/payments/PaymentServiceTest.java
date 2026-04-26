package com.fintech.payments;

import com.fintech.payments.exception.FraudDetectedException;
import com.fintech.payments.exception.InsufficientFundsException;
import com.fintech.payments.kafka.PaymentEventProducer;
import com.fintech.payments.model.*;
import com.fintech.payments.repository.TransactionRepository;
import com.fintech.payments.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountService accountService;
    @Mock private FraudDetectionService fraudDetectionService;
    @Mock private PaymentEventProducer eventProducer;
    @Mock private IdempotencyService idempotencyService;

    @InjectMocks
    private PaymentService paymentService;

    private Account sourceAccount;
    private Account destAccount;
    private PaymentRequest request;
    private FraudScore lowRiskScore;

    @BeforeEach
    void setUp() {
        sourceAccount = Account.builder()
                .id("src-001")
                .accountNumber("ACC1001")
                .accountHolderName("Alice")
                .balance(new BigDecimal("10000.00"))
                .currency("USD")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now().minusDays(60))
                .updatedAt(LocalDateTime.now())
                .build();

        destAccount = Account.builder()
                .id("dst-002")
                .accountNumber("ACC1002")
                .accountHolderName("Bob")
                .balance(new BigDecimal("5000.00"))
                .currency("USD")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now().minusDays(30))
                .updatedAt(LocalDateTime.now())
                .build();

        request = PaymentRequest.builder()
                .sourceAccountId("src-001")
                .destinationAccountId("dst-002")
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .idempotencyKey("test-key-001")
                .description("Test payment")
                .build();

        lowRiskScore = FraudScore.builder()
                .score(0.1)
                .mlScore(0.1)
                .ruleViolations(Collections.emptyList())
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Test
    void processPayment_success() {
        when(idempotencyService.isDuplicate(anyString())).thenReturn(false);
        when(accountService.getAccount("src-001")).thenReturn(sourceAccount);
        when(accountService.getAccount("dst-002")).thenReturn(destAccount);
        when(fraudDetectionService.evaluateTransaction(any(), any(), any())).thenReturn(lowRiskScore);
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PaymentResponse response = paymentService.processPayment(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));

        verify(accountService).debit(eq("src-001"), eq(new BigDecimal("500.00")), anyString());
        verify(accountService).credit(eq("dst-002"), eq(new BigDecimal("500.00")), anyString());
        verify(eventProducer).publishPaymentCompleted(any());
    }

    @Test
    void processPayment_insufficientFunds() {
        sourceAccount.setBalance(new BigDecimal("100.00")); // less than 500

        when(idempotencyService.isDuplicate(anyString())).thenReturn(false);
        when(accountService.getAccount("src-001")).thenReturn(sourceAccount);
        when(accountService.getAccount("dst-002")).thenReturn(destAccount);
        when(fraudDetectionService.evaluateTransaction(any(), any(), any())).thenReturn(lowRiskScore);

        assertThatThrownBy(() -> paymentService.processPayment(request))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("src-001");

        verify(accountService, never()).debit(any(), any(), any());
    }

    @Test
    void processPayment_fraudDetected() {
        FraudScore highRiskScore = FraudScore.builder()
                .score(0.95)
                .mlScore(0.95)
                .ruleViolations(List.of("LARGE_TRANSACTION", "HIGH_VELOCITY"))
                .timestamp(LocalDateTime.now())
                .build();

        when(idempotencyService.isDuplicate(anyString())).thenReturn(false);
        when(accountService.getAccount("src-001")).thenReturn(sourceAccount);
        when(accountService.getAccount("dst-002")).thenReturn(destAccount);
        when(fraudDetectionService.evaluateTransaction(any(), any(), any())).thenReturn(highRiskScore);

        assertThatThrownBy(() -> paymentService.processPayment(request))
                .isInstanceOf(FraudDetectedException.class);

        verify(accountService, never()).debit(any(), any(), any());
    }

    @Test
    void processPayment_idempotency() {
        PaymentResponse cachedResponse = PaymentResponse.builder()
                .transactionId("cached-txn-001")
                .status("COMPLETED")
                .success(true)
                .amount(new BigDecimal("500.00"))
                .build();

        when(idempotencyService.isDuplicate("test-key-001")).thenReturn(true);
        when(idempotencyService.getResult("test-key-001")).thenReturn(cachedResponse);

        PaymentResponse response = paymentService.processPayment(request);

        assertThat(response.getTransactionId()).isEqualTo("cached-txn-001");
        verify(accountService, never()).getAccount(any());
    }

    @Test
    void processPayment_sameAccountRejected() {
        request.setDestinationAccountId("src-001"); // same as source

        when(idempotencyService.isDuplicate(anyString())).thenReturn(false);
        when(accountService.getAccount("src-001")).thenReturn(sourceAccount);

        assertThatThrownBy(() -> paymentService.processPayment(request))
                .isInstanceOf(com.fintech.payments.exception.InvalidAccountException.class)
                .hasMessageContaining("same account");
    }
}
