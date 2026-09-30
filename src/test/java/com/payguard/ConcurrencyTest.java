package com.payguard;

import com.payguard.dto.PaymentRequest;
import com.payguard.model.Account;
import com.payguard.model.TransactionStatus;
import com.payguard.repository.AccountRepository;
import com.payguard.repository.TransactionRepository;
import com.payguard.service.PaymentResult;
import com.payguard.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hammers the service from many threads at once and checks that
 * (1) no money is created or lost, and (2) a repeated Idempotency-Key never double-charges.
 */
@SpringBootTest(properties = {
        "payguard.fraud.velocity.max-per-window=100000",
        "payguard.transfer.max-retries=50"
})
@ActiveProfiles("test")
class ConcurrencyTest {

    @Autowired PaymentService paymentService;
    @Autowired AccountRepository accountRepository;
    @Autowired TransactionRepository transactionRepository;

    @Test
    void concurrentTransfersNeverLoseOrCreateMoney() throws Exception {
        Account sender = accountRepository.save(new Account("Sender", new BigDecimal("1000.00")));
        Account receiver = accountRepository.save(new Account("Receiver", new BigDecimal("0.00")));

        int payments = 50;
        List<Callable<PaymentResult>> tasks = new ArrayList<>();
        for (int i = 0; i < payments; i++) {
            tasks.add(() -> paymentService.pay(UUID.randomUUID().toString(),
                    new PaymentRequest(sender.getId(), receiver.getId(), new BigDecimal("10.00"))));
        }

        runAllAtOnce(tasks, 10);

        BigDecimal senderBalance = accountRepository.findById(sender.getId()).orElseThrow().getBalance();
        BigDecimal receiverBalance = accountRepository.findById(receiver.getId()).orElseThrow().getBalance();

        assertThat(senderBalance).isEqualByComparingTo("500.00");
        assertThat(receiverBalance).isEqualByComparingTo("500.00");
        assertThat(transactionRepository.findHistory(sender.getId(), Pageable.unpaged()).getTotalElements())
                .isEqualTo(payments);
    }

    @Test
    void sameIdempotencyKeyFromManyThreadsChargesExactlyOnce() throws Exception {
        Account sender = accountRepository.save(new Account("Sender", new BigDecimal("1000.00")));
        Account receiver = accountRepository.save(new Account("Receiver", new BigDecimal("0.00")));

        String key = "retry-storm-" + UUID.randomUUID();
        PaymentRequest request = new PaymentRequest(sender.getId(), receiver.getId(), new BigDecimal("250.00"));

        List<Callable<PaymentResult>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> paymentService.pay(key, request));
        }

        List<PaymentResult> results = runAllAtOnce(tasks, 10);

        assertThat(accountRepository.findById(sender.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("750.00");
        assertThat(results).allSatisfy(r -> assertThat(r.transaction().status()).isEqualTo(TransactionStatus.APPROVED));
        // Every caller sees the SAME transaction id
        assertThat(results.stream().map(r -> r.transaction().transactionId()).distinct()).hasSize(1);
        assertThat(results.stream().filter(r -> !r.replayed())).hasSize(1);
    }

    /** Releases all tasks through a latch so they genuinely hit the DB at the same moment. */
    private static <T> List<T> runAllAtOnce(List<Callable<T>> tasks, int threads) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
