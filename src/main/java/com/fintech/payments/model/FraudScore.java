package com.fintech.payments.model;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudScore {

    private double score;
    private double mlScore;
    private List<String> ruleViolations;
    private Map<String, Object> features;
    private LocalDateTime timestamp;

    private static final double HIGH_RISK_THRESHOLD = 0.75;

    public boolean isHighRisk() {
        return score >= HIGH_RISK_THRESHOLD;
    }

    public String getRiskLevel() {
        if (score < 0.3) return "LOW";
        if (score < 0.6) return "MEDIUM";
        if (score < 0.75) return "HIGH";
        return "CRITICAL";
    }
}
