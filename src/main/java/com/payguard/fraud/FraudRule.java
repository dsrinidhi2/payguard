package com.payguard.fraud;

import com.payguard.dto.PaymentRequest;

/**
 * Strategy pattern: every fraud check is its own class implementing this interface.
 * Adding a new rule = adding one @Component. The engine and the payment service never change
 * (Open/Closed principle).
 */
public interface FraudRule {

    /** Short name that shows up in the transaction's "reason" field. */
    String name();

    FraudDecision evaluate(PaymentRequest request);
}
