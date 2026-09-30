package com.payguard.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String holderName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    /**
     * Optimistic locking: Hibernate adds "WHERE version = ?" to every UPDATE.
     * If two transfers read the same version, only one commit succeeds —
     * the other gets an OptimisticLockException and is retried.
     */
    @Version
    private Long version;

    private Instant createdAt = Instant.now();

    protected Account() {
    }

    public Account(String holderName, BigDecimal balance) {
        this.holderName = holderName;
        this.balance = balance;
    }

    public void debit(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }

    public boolean hasSufficientFunds(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }

    public Long getId() { return id; }
    public String getHolderName() { return holderName; }
    public BigDecimal getBalance() { return balance; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}
