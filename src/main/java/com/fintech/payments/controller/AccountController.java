package com.fintech.payments.controller;

import com.fintech.payments.model.Account;
import com.fintech.payments.model.AccountLedger;
import com.fintech.payments.service.AccountService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /**
     * Create a new account.
     */
    @PostMapping
    public ResponseEntity<Account> createAccount(@RequestBody Map<String, Object> body) {
        String holderName = (String) body.get("holderName");
        BigDecimal initialBalance = body.containsKey("initialBalance")
                ? new BigDecimal(body.get("initialBalance").toString())
                : BigDecimal.ZERO;
        String currency = (String) body.getOrDefault("currency", "USD");

        Account account = accountService.createAccount(holderName, initialBalance, currency);
        log.info("Created account: {} for {}", account.getId(), holderName);
        return ResponseEntity.status(HttpStatus.CREATED).body(account);
    }

    /**
     * Get account by ID.
     */
    @GetMapping("/{accountId}")
    public ResponseEntity<Account> getAccount(@PathVariable String accountId) {
        Account account = accountService.getAccount(accountId);
        return ResponseEntity.ok(account);
    }

    /**
     * Get account ledger (full audit trail of debits/credits).
     */
    @GetMapping("/{accountId}/ledger")
    public ResponseEntity<List<AccountLedger>> getLedger(@PathVariable String accountId) {
        List<AccountLedger> ledger = accountService.getLedger(accountId);
        return ResponseEntity.ok(ledger);
    }
}
