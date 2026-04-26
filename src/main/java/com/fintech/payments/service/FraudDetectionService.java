package com.fintech.payments.service;

import com.fintech.payments.model.*;
import com.fintech.payments.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudDetectionService {

    private final TransactionRepository transactionRepository;
    private final RestTemplate restTemplate;

    @Value("${fraud.ml-service.url:http://ml-fraud-service:5000/predict}")
    private String mlServiceUrl;

    @Value("${fraud.ml-service.enabled:false}")
    private boolean mlServiceEnabled;

    public FraudScore evaluateTransaction(
            PaymentRequest request,
            Account source,
            Account destination) {

        Map<String, Object> features = extractFeatures(request, source, destination);
        double mlScore = mlServiceEnabled ? callMLModel(features) : computeHeuristicScore(features);
        List<String> ruleViolations = applyRules(request, source, destination);
        double finalScore = combineScores(mlScore, ruleViolations);

        FraudScore fraudScore = FraudScore.builder()
                .score(finalScore)
                .mlScore(mlScore)
                .ruleViolations(ruleViolations)
                .features(features)
                .timestamp(LocalDateTime.now())
                .build();

        log.info("Fraud score for {}: {} ({})", request.getIdempotencyKey(), finalScore, fraudScore.getRiskLevel());
        return fraudScore;
    }

    private Map<String, Object> extractFeatures(PaymentRequest request, Account source, Account destination) {
        Map<String, Object> features = new HashMap<>();

        features.put("amount", request.getAmount().doubleValue());
        features.put("currency", request.getCurrency());
        features.put("hour_of_day", LocalDateTime.now().getHour());
        features.put("day_of_week", LocalDateTime.now().getDayOfWeek().getValue());

        features.put("source_account_age_days", getAccountAgeDays(source.getCreatedAt()));
        features.put("source_account_balance", source.getBalance().doubleValue());
        features.put("destination_account_age_days", getAccountAgeDays(destination.getCreatedAt()));

        List<Transaction> recentTxns = transactionRepository
                .findBySourceAccountIdAndCreatedAtAfter(source.getId(), LocalDateTime.now().minusDays(30));

        features.put("transactions_last_30_days", recentTxns.size());
        features.put("avg_transaction_amount_30_days", calculateAvgAmount(recentTxns));
        features.put("max_transaction_amount_30_days", calculateMaxAmount(recentTxns));

        long txnsLastHour = transactionRepository
                .countBySourceAccountIdAndCreatedAtAfter(source.getId(), LocalDateTime.now().minusHours(1));
        features.put("transactions_last_hour", txnsLastHour);

        boolean firstTimeRecipient = !transactionRepository
                .existsBySourceAccountIdAndDestinationAccountId(source.getId(), destination.getId());
        features.put("first_time_recipient", firstTimeRecipient ? 1.0 : 0.0);

        double typicalAmount = calculateTypicalAmount(source.getId());
        if (typicalAmount > 0) {
            double deviation = Math.abs(request.getAmount().doubleValue() - typicalAmount) / typicalAmount;
            features.put("amount_deviation_from_typical", deviation);
        } else {
            features.put("amount_deviation_from_typical", 0.0);
        }

        return features;
    }

    private double callMLModel(Map<String, Object> features) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("features", features);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(mlServiceUrl, requestBody, Map.class);

            if (response != null && response.containsKey("fraud_probability")) {
                return ((Number) response.get("fraud_probability")).doubleValue();
            }
        } catch (Exception e) {
            log.error("ML model call failed, falling back to heuristic scoring", e);
        }
        return computeHeuristicScore(features);
    }

    /**
     * Heuristic scoring when ML service is unavailable.
     * Uses statistical signals from feature values.
     */
    private double computeHeuristicScore(Map<String, Object> features) {
        double score = 0.1; // baseline

        double amount = (double) features.getOrDefault("amount", 0.0);
        if (amount > 10000) score += 0.2;
        else if (amount > 5000) score += 0.1;

        long txnsLastHour = ((Number) features.getOrDefault("transactions_last_hour", 0L)).longValue();
        if (txnsLastHour > 5) score += 0.3;
        else if (txnsLastHour > 3) score += 0.1;

        double firstTimeRecipient = (double) features.getOrDefault("first_time_recipient", 0.0);
        if (firstTimeRecipient == 1.0 && amount > 1000) score += 0.15;

        long sourceAge = ((Number) features.getOrDefault("source_account_age_days", 365L)).longValue();
        if (sourceAge < 7 && amount > 1000) score += 0.2;

        double deviation = (double) features.getOrDefault("amount_deviation_from_typical", 0.0);
        if (deviation > 5.0) score += 0.15;
        else if (deviation > 2.0) score += 0.05;

        return Math.min(score, 1.0);
    }

    private List<String> applyRules(PaymentRequest request, Account source, Account destination) {
        List<String> violations = new ArrayList<>();

        if (request.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            violations.add("LARGE_TRANSACTION");
        }

        boolean isNewRecipient = !transactionRepository
                .existsBySourceAccountIdAndDestinationAccountId(source.getId(), destination.getId());
        if (isNewRecipient && request.getAmount().compareTo(new BigDecimal("5000")) > 0) {
            violations.add("NEW_RECIPIENT_HIGH_AMOUNT");
        }

        long recentCount = transactionRepository
                .countBySourceAccountIdAndCreatedAtAfter(source.getId(), LocalDateTime.now().minusHours(1));
        if (recentCount > 5) {
            violations.add("HIGH_VELOCITY");
        }

        if (isRoundNumber(request.getAmount())) {
            violations.add("ROUND_AMOUNT");
        }

        long accountAgeDays = getAccountAgeDays(source.getCreatedAt());
        if (accountAgeDays < 7 && request.getAmount().compareTo(new BigDecimal("1000")) > 0) {
            violations.add("NEW_ACCOUNT_LARGE_TRANSACTION");
        }

        return violations;
    }

    private double combineScores(double mlScore, List<String> ruleViolations) {
        double ruleScore = Math.min(ruleViolations.size() * 0.2, 1.0);
        return (mlScore * 0.7) + (ruleScore * 0.3);
    }

    private long getAccountAgeDays(LocalDateTime createdAt) {
        return Duration.between(createdAt, LocalDateTime.now()).toDays();
    }

    private double calculateAvgAmount(List<Transaction> transactions) {
        if (transactions.isEmpty()) return 0.0;
        return transactions.stream()
                .map(Transaction::getAmount)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0.0);
    }

    private double calculateMaxAmount(List<Transaction> transactions) {
        if (transactions.isEmpty()) return 0.0;
        return transactions.stream()
                .map(Transaction::getAmount)
                .mapToDouble(BigDecimal::doubleValue)
                .max()
                .orElse(0.0);
    }

    private double calculateTypicalAmount(String accountId) {
        List<Transaction> history = transactionRepository
                .findBySourceAccountIdAndCreatedAtAfter(accountId, LocalDateTime.now().minusDays(90));
        return calculateAvgAmount(history);
    }

    private boolean isRoundNumber(BigDecimal amount) {
        return amount.remainder(new BigDecimal("100")).compareTo(BigDecimal.ZERO) == 0;
    }
}
