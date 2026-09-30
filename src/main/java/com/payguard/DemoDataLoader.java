package com.payguard;

import com.payguard.model.Account;
import com.payguard.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Seeds two demo accounts so you can try the API straight away. Skipped during tests. */
@Component
@Profile("!test")
public class DemoDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final AccountRepository accountRepository;

    public DemoDataLoader(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public void run(String... args) {
        Account alice = accountRepository.save(new Account("Alice", new BigDecimal("100000.00")));
        Account bob = accountRepository.save(new Account("Bob", new BigDecimal("5000.00")));
        log.info("Demo accounts ready -> Alice id={}, Bob id={}. Swagger UI: http://localhost:8080/swagger-ui.html",
                alice.getId(), bob.getId());
    }
}
