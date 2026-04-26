package com.fintech.payments.repository;

import com.fintech.payments.model.AccountLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountLedgerRepository extends JpaRepository<AccountLedger, Long> {

    List<AccountLedger> findByAccountIdOrderByCreatedAtDesc(String accountId);

    List<AccountLedger> findByTransactionId(String transactionId);
}
