package com.payguard.fraud;

import com.payguard.dto.PaymentRequest;
import com.payguard.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Velocity check: too many payments from one account in a short window is a classic
 * sign of a stolen card or a compromised account being drained. These get declined outright.
 */
@Component
public class VelocityRule implements FraudRule {

    private final TransactionRepository transactionRepository;
    private final int maxPerWindow;
    private final long windowSeconds;

    public VelocityRule(TransactionRepository transactionRepository,
                        @Value("${payguard.fraud.velocity.max-per-window}") int maxPerWindow,
                        @Value("${payguard.fraud.velocity.window-seconds}") long windowSeconds) {
        this.transactionRepository = transactionRepository;
        this.maxPerWindow = maxPerWindow;
        this.windowSeconds = windowSeconds;
    }

    @Override
    public String name() {
        return "VELOCITY";
    }

    @Override
    public FraudDecision evaluate(PaymentRequest request) {
        Instant windowStart = Instant.now().minusSeconds(windowSeconds);
        long recent = transactionRepository.countByFromAccountIdAndCreatedAtAfter(request.fromAccountId(), windowStart);
        if (recent >= maxPerWindow) {
            return FraudDecision.decline(name() + ": more than " + maxPerWindow
                    + " payments in " + windowSeconds + "s");
        }
        return FraudDecision.approve();
    }
}
