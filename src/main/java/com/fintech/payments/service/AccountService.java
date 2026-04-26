package com.fintech.payments.service;

import com.fintech.payments.exception.InvalidAccountException;
import com.fintech.payments.model.Account;
import com.fintech.payments.model.AccountLedger;
import com.fintech.payments.repository.AccountLedgerRepository;
import com.fintech.payments.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountLedgerRepository ledgerRepository;

    public Account getAccount(String accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new InvalidAccountException("Account not found: " + accountId));
    }

    public Account createAccount(String holderName, BigDecimal initialBalance, String currency) {
        Account account = Account.builder()
                .id(UUID.randomUUID().toString())
                .accountNumber(generateAccountNumber())
                .accountHolderName(holderName)
                .balance(initialBalance != null ? initialBalance : BigDecimal.ZERO)
                .currency(currency != null ? currency : "USD")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return accountRepository.save(account);
    }

    public void debit(String accountId, BigDecimal amount, String transactionId) {
        int updated = accountRepository.debitBalance(accountId, amount);
        if (updated == 0) {
            throw new InvalidAccountException("Failed to debit account: " + accountId + ". Insufficient funds or account not found.");
        }
        BigDecimal balanceAfter = accountRepository.findById(accountId)
                .map(Account::getBalance).orElse(BigDecimal.ZERO);
        saveLedgerEntry(accountId, transactionId, amount.negate(), balanceAfter, "DEBIT");
        log.info("Debited {} from account {}", amount, accountId);
    }

    public void credit(String accountId, BigDecimal amount, String transactionId) {
        int updated = accountRepository.creditBalance(accountId, amount);
        if (updated == 0) {
            throw new InvalidAccountException("Failed to credit account: " + accountId);
        }
        BigDecimal balanceAfter = accountRepository.findById(accountId)
                .map(Account::getBalance).orElse(BigDecimal.ZERO);
        saveLedgerEntry(accountId, transactionId, amount, balanceAfter, "CREDIT");
        log.info("Credited {} to account {}", amount, accountId);
    }

    public List<AccountLedger> getLedger(String accountId) {
        return ledgerRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
    }

    private void saveLedgerEntry(String accountId, String transactionId,
                                  BigDecimal amount, BigDecimal balanceAfter, String operationType) {
        AccountLedger entry = AccountLedger.builder()
                .accountId(accountId)
                .transactionId(transactionId)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .operationType(operationType)
                .createdAt(LocalDateTime.now())
                .build();
        ledgerRepository.save(entry);
    }

    private String generateAccountNumber() {
        return "ACC" + System.currentTimeMillis();
    }
}
