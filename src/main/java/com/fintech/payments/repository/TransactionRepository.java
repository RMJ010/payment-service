package com.fintech.payments.repository;

import com.fintech.payments.model.Transaction;
import com.fintech.payments.model.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    List<Transaction> findBySourceAccountIdAndCreatedAtAfter(
            String sourceAccountId, LocalDateTime after);

    List<Transaction> findByDestinationAccountIdAndCreatedAtAfter(
            String destinationAccountId, LocalDateTime after);

    long countBySourceAccountIdAndCreatedAtAfter(
            String sourceAccountId, LocalDateTime after);

    boolean existsBySourceAccountIdAndDestinationAccountId(
            String sourceAccountId, String destinationAccountId);

    List<Transaction> findBySourceAccountIdOrderByCreatedAtDesc(String sourceAccountId);

    List<Transaction> findByStatusOrderByCreatedAtDesc(TransactionStatus status);

    @Query("SELECT t FROM Transaction t WHERE t.fraudScore >= :threshold ORDER BY t.createdAt DESC")
    List<Transaction> findHighFraudTransactions(double threshold);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.status = 'COMPLETED' AND t.createdAt >= :since")
    long countCompletedSince(LocalDateTime since);
}
