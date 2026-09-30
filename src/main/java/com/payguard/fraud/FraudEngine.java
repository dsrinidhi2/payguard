package com.payguard.fraud;

import com.payguard.dto.PaymentRequest;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Runs every FraudRule bean and returns the most severe decision.
 * Spring injects all FraudRule implementations into the list automatically.
 */
@Service
public class FraudEngine {

    private final List<FraudRule> rules;

    public FraudEngine(List<FraudRule> rules) {
        this.rules = rules;
    }

    public FraudDecision evaluate(PaymentRequest request) {
        return rules.stream()
                .map(rule -> rule.evaluate(request))
                .max(Comparator.comparingInt(FraudDecision::severity))
                .orElse(FraudDecision.approve());
    }
}
