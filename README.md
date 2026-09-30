# PayGuard 💳

A payment transaction service built with **Spring Boot 3**. It moves money between accounts **exactly once**, even under retries and concurrent load, and runs every payment through a pluggable fraud-rule engine.

## Features

| Feature | How it works |
|---|---|
| **Idempotent payments** | Clients send an `Idempotency-Key` header. A retried request returns the original result (`200` + `Idempotent-Replayed: true`) instead of charging twice. Reusing a key for a *different* payment returns `409`. |
| **No double-spend** | `@Version` optimistic locking on `Account` + a retry loop with jittered exponential backoff, *outside* the DB transaction. |
| **Atomic transfers** | Debit, credit, transaction record and idempotency record all commit in one DB transaction, or none of them do. |
| **Fraud engine** | Strategy pattern: each rule is a `FraudRule` bean, auto-collected by `FraudEngine`. `HighAmountRule` → **FLAGGED** (held for review), `VelocityRule` → **DECLINED** (too many payments per minute). |
| **Clean errors** | `@RestControllerAdvice` returns RFC 7807 `problem+json` for 400 / 404 / 409 / 422 / 503. |
| **Paginated history** | Sent + received transactions, newest first. |
| **Concurrency tests** | 50 parallel transfers → balances still add up exactly. 10 parallel requests with the same key → charged once. |

## Tech stack
Java 17 · Spring Boot 3.3 · Spring Data JPA (Hibernate) · H2 · Bean Validation · springdoc OpenAPI (Swagger) · JUnit 5 · MockMvc

## Run it

Requires **Java 17+** and **Maven** (or just open the folder in IntelliJ and run `PayGuardApplication`).

```bash
mvn test              # run all tests
mvn spring-boot:run   # start on http://localhost:8080
```

Two demo accounts are created on startup: **Alice (id 1, ₹1,00,000)** and **Bob (id 2, ₹5,000)**.

- Swagger UI: http://localhost:8080/swagger-ui.html
- H2 console: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:payguard`, user `sa`, no password)

## Try it

```bash
# 1. Pay Bob 500 — returns 201 Created
curl -i -X POST localhost:8080/api/payments \
  -H "Content-Type: application/json" -H "Idempotency-Key: order-123" \
  -d '{"fromAccountId":1,"toAccountId":2,"amount":500}'

# 2. Send the EXACT same request again (e.g. client timed out and retried)
#    → 200 OK, Idempotent-Replayed: true, same transactionId, no second charge
curl -i -X POST localhost:8080/api/payments \
  -H "Content-Type: application/json" -H "Idempotency-Key: order-123" \
  -d '{"fromAccountId":1,"toAccountId":2,"amount":500}'

# 3. Large payment → status FLAGGED, money not moved
curl -X POST localhost:8080/api/payments \
  -H "Content-Type: application/json" -H "Idempotency-Key: big-1" \
  -d '{"fromAccountId":1,"toAccountId":2,"amount":75000}'

# 4. Bob tries to overspend → 422 Insufficient funds
curl -X POST localhost:8080/api/payments \
  -H "Content-Type: application/json" -H "Idempotency-Key: oops-1" \
  -d '{"fromAccountId":2,"toAccountId":1,"amount":999999}'

# 5. Balances and history
curl localhost:8080/api/accounts/1
curl "localhost:8080/api/accounts/1/transactions?page=0&size=5"
```

## API

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/accounts` | Open an account `{ "holderName", "openingBalance" }` |
| `GET` | `/api/accounts/{id}` | Account + balance |
| `GET` | `/api/accounts/{id}/transactions?page=&size=` | Paginated history |
| `POST` | `/api/payments` | Transfer (header `Idempotency-Key` required) `{ "fromAccountId", "toAccountId", "amount" }` |

## Design notes

**Why optimistic locking, not `SELECT ... FOR UPDATE`?**
Most payments touch different accounts, so conflicts are rare. Optimistic locking takes no locks on the happy path and only pays a cost (a retry) when two payments really do collide. Pessimistic locking would serialise every payment on an account even when there's no conflict.

**Why is the retry loop outside `@Transactional`?**
When an optimistic lock fails, that transaction is already rolled back and its entities are stale. Each retry has to start a fresh transaction and re-read the latest balance.

**How is idempotency race-safe?**
The idempotency key is the **primary key** of `idempotency_records`, inserted in the same transaction as the transfer. If two requests with the same key race, the database lets exactly one insert succeed. The loser's whole transaction (including its debit) rolls back, and it replays the winner's result.

**Why `BigDecimal`?**
`double` can't represent 0.1 exactly. Money needs exact decimal arithmetic.

## What I'd add for production
- Postgres instead of H2 (swap the datasource; the code doesn't change)
- Expire old idempotency keys (e.g. after 24h) with a scheduled job
- Async fraud scoring over Kafka, plus ML-based risk scores
- Auth (Spring Security + JWT) so users can only move money from their own accounts
- Outbox pattern to publish `PaymentCompleted` events reliably
