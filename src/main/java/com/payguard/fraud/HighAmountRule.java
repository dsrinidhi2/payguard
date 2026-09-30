package com.payguard.fraud;

import com.payguard.dto.PaymentRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Unusually large payments aren't blocked, but are held for a human to review. */
@Component
public class HighAmountRule implements FraudRule {

    private final BigDecimal threshold;

    public HighAmountRule(@Value("${payguard.fraud.high-amount-threshold}") BigDecimal threshold) {
        this.threshold = threshold;
    }

    @Override
    public String name() {
        return "HIGH_AMOUNT";
    }

    @Override
    public FraudDecision evaluate(PaymentRequest request) {
        if (request.amount().compareTo(threshold) > 0) {
            return FraudDecision.flag(name() + ": amount exceeds " + threshold.toPlainString());
        }
        return FraudDecision.approve();
    }
}
