package com.fintech.payments.exception;

import com.fintech.payments.model.FraudScore;

public class FraudDetectedException extends RuntimeException {
    private final FraudScore fraudScore;

    public FraudDetectedException(FraudScore fraudScore) {
        super(String.format("Fraud detected. Score: %.4f, Risk: %s, Violations: %s",
                fraudScore.getScore(),
                fraudScore.getRiskLevel(),
                fraudScore.getRuleViolations()));
        this.fraudScore = fraudScore;
    }

    public FraudScore getFraudScore() { return fraudScore; }
}
