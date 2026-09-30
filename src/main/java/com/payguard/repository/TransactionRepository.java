package com.payguard.repository;

import com.payguard.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /** Velocity check: how many payments has this account sent since a given time? */
    long countByFromAccountIdAndCreatedAtAfter(Long fromAccountId, Instant since);

    /** Full history for an account — both money sent and money received. Sorting comes from the Pageable. */
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.fromAccountId = :accountId OR t.toAccountId = :accountId
            """)
    Page<Transaction> findHistory(@Param("accountId") Long accountId, Pageable pageable);
}
