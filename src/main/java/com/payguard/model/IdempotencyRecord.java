package com.payguard.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Remembers which transaction an Idempotency-Key produced.
 * The primary key IS the idempotency key, so the database itself
 * guarantees two requests with the same key can never both be saved.
 */
@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {

    @Id
    @Column(name = "idem_key", length = 100)
    private String key;

    /** Hash of the request body — catches a client reusing a key for a different payment. */
    @Column(nullable = false)
    private String requestFingerprint;

    @Column(nullable = false)
    private Long transactionId;

    private Instant createdAt = Instant.now();

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(String key, String requestFingerprint, Long transactionId) {
        this.key = key;
        this.requestFingerprint = requestFingerprint;
        this.transactionId = transactionId;
    }

    public String getKey() { return key; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public Long getTransactionId() { return transactionId; }
    public Instant getCreatedAt() { return createdAt; }
}
