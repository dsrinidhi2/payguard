package com.payguard.service;

import com.payguard.dto.PaymentRequest;
import com.payguard.dto.TransactionResponse;
import com.payguard.exception.PayGuardException;
import com.payguard.fraud.FraudDecision;
import com.payguard.fraud.FraudEngine;
import com.payguard.model.Account;
import com.payguard.model.IdempotencyRecord;
import com.payguard.model.Transaction;
import com.payguard.model.TransactionStatus;
import com.payguard.repository.AccountRepository;
import com.payguard.repository.IdempotencyRepository;
import com.payguard.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final FraudEngine fraudEngine;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;
    private final int maxRetries;

    public PaymentService(AccountRepository accountRepository,
                          TransactionRepository transactionRepository,
                          IdempotencyRepository idempotencyRepository,
                          FraudEngine fraudEngine,
                          EntityManager entityManager,
                          TransactionTemplate transactionTemplate,
                          @Value("${payguard.transfer.max-retries}") int maxRetries) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.fraudEngine = fraudEngine;
        this.entityManager = entityManager;
        this.transactionTemplate = transactionTemplate;
        this.maxRetries = maxRetries;
    }

    /**
     * Moves money exactly once per Idempotency-Key.
     *
     * The retry loop lives OUTSIDE the database transaction on purpose: when an optimistic
     * lock fails, that whole transaction is dead and rolled back, so each retry must start
     * a brand-new transaction and re-read the latest balances.
     */
    public PaymentResult pay(String idempotencyKey, PaymentRequest request) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw PayGuardException.badRequest("Cannot send money to the same account");
        }

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            // Check on EVERY attempt: while we were backing off, a request with this same key may have committed.
            var existing = idempotencyRepository.findById(idempotencyKey);
            if (existing.isPresent()) {
                return replay(existing.get(), request);
            }
            try {
                TransactionResponse response = transactionTemplate.execute(status -> transfer(idempotencyKey, request));
                return new PaymentResult(response, false);
            } catch (DataIntegrityViolationException e) {
                // Two requests with the SAME key raced past the check above.
                // The DB primary key let exactly one win — hand this caller the winner's result.
                return idempotencyRepository.findById(idempotencyKey)
                        .map(record -> replay(record, request))
                        .orElseThrow(() -> e);
            } catch (DataAccessException e) {
                if (!isConcurrentUpdate(e)) {
                    throw e;
                }
                // Another payment touched the same account between our read and our write.
                log.debug("Concurrent update on attempt {}/{} for key {}", attempt, maxRetries, idempotencyKey);
                backoff(attempt);
            }
        }
        throw PayGuardException.tooMuchContention();
    }

    /**
     * Optimistic-lock failures, lock timeouts and deadlocks all arrive as ConcurrencyFailureException.
     * H2 can also report a raw "concurrent update" error (code 90131) that Hibernate doesn't classify,
     * so we check the SQL cause too. Postgres reports these as SQLState 40001.
     */
    private static boolean isConcurrentUpdate(DataAccessException e) {
        if (e instanceof ConcurrencyFailureException) {
            return true;
        }
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && (sql.getErrorCode() == 90131 || "40001".equals(sql.getSQLState()))) {
                return true;
            }
        }
        return false;
    }

    /** Runs inside one DB transaction: either every write below commits, or none of them do. */
    private TransactionResponse transfer(String idempotencyKey, PaymentRequest request) {
        Account from = accountRepository.findById(request.fromAccountId())
                .orElseThrow(() -> PayGuardException.notFound("Account " + request.fromAccountId() + " not found"));
        Account to = accountRepository.findById(request.toAccountId())
                .orElseThrow(() -> PayGuardException.notFound("Account " + request.toAccountId() + " not found"));

        FraudDecision decision = fraudEngine.evaluate(request);

        if (decision.status() == TransactionStatus.APPROVED) {
            if (!from.hasSufficientFunds(request.amount())) {
                throw PayGuardException.insufficientFunds(from.getId());
            }
            from.debit(request.amount());
            to.credit(request.amount());
            // No explicit save() needed: both accounts are managed entities, so Hibernate
            // flushes the new balances on commit, checking @Version on each row.
        }

        Transaction txn = transactionRepository.save(new Transaction(
                from.getId(), to.getId(), request.amount(), decision.status(), decision.reason()));

        // persist() (not save/merge) forces a plain INSERT, so a duplicate key fails loudly
        // instead of silently overwriting the earlier record.
        entityManager.persist(new IdempotencyRecord(idempotencyKey, request.fingerprint(), txn.getId()));

        return TransactionResponse.from(txn);
    }

    private PaymentResult replay(IdempotencyRecord record, PaymentRequest request) {
        if (!record.getRequestFingerprint().equals(request.fingerprint())) {
            throw PayGuardException.idempotencyConflict(record.getKey());
        }
        Transaction original = transactionRepository.findById(record.getTransactionId()).orElseThrow();
        return new PaymentResult(TransactionResponse.from(original), true);
    }

    /** Randomised exponential backoff so competing threads don't retry in lockstep. */
    private void backoff(int attempt) {
        long maxSleepMs = Math.min(200, 5L << attempt);
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(1, maxSleepMs + 1));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw PayGuardException.tooMuchContention();
        }
    }
}
