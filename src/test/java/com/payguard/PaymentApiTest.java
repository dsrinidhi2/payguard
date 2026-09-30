package com.payguard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payguard.dto.PaymentRequest;
import com.payguard.model.Account;
import com.payguard.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** End-to-end HTTP tests: request in, JSON + status code out. */
@SpringBootTest(properties = "payguard.fraud.velocity.max-per-window=3")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accountRepository;

    Account alice;
    Account bob;

    @BeforeEach
    void freshAccounts() {
        alice = accountRepository.save(new Account("Alice", new BigDecimal("100000.00")));
        bob = accountRepository.save(new Account("Bob", new BigDecimal("100.00")));
    }

    @Test
    void successfulPaymentMovesMoney() throws Exception {
        pay(key(), alice.getId(), bob.getId(), "250.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mvc.perform(get("/api/accounts/" + bob.getId()))
                .andExpect(jsonPath("$.balance").value(350.00));
    }

    @Test
    void retryWithSameKeyReturnsOriginalAndDoesNotChargeTwice() throws Exception {
        String key = key();
        String firstId = pay(key, alice.getId(), bob.getId(), "100.00")
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andReturn().getResponse().getContentAsString();

        pay(key, alice.getId(), bob.getId(), "100.00")
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(content().json(firstId));

        mvc.perform(get("/api/accounts/" + alice.getId()))
                .andExpect(jsonPath("$.balance").value(99900.00));
    }

    @Test
    void reusingKeyForDifferentPaymentIsRejected() throws Exception {
        String key = key();
        pay(key, alice.getId(), bob.getId(), "100.00").andExpect(status().isCreated());
        pay(key, alice.getId(), bob.getId(), "999.00").andExpect(status().isConflict());
    }

    @Test
    void insufficientFundsReturns422AndMovesNothing() throws Exception {
        pay(key(), bob.getId(), alice.getId(), "500.00")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Insufficient funds in account " + bob.getId()));

        mvc.perform(get("/api/accounts/" + bob.getId()))
                .andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    void highAmountIsFlaggedAndNotMoved() throws Exception {
        pay(key(), alice.getId(), bob.getId(), "60000.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FLAGGED"));

        mvc.perform(get("/api/accounts/" + alice.getId()))
                .andExpect(jsonPath("$.balance").value(100000.00));
    }

    @Test
    void tooManyPaymentsInAMinuteAreDeclined() throws Exception {
        for (int i = 0; i < 3; i++) {
            pay(key(), alice.getId(), bob.getId(), "1.00").andExpect(jsonPath("$.status").value("APPROVED"));
        }
        pay(key(), alice.getId(), bob.getId(), "1.00")
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.reason").value(org.hamcrest.Matchers.startsWith("VELOCITY")));
    }

    @Test
    void historyIsPaginatedNewestFirst() throws Exception {
        pay(key(), alice.getId(), bob.getId(), "1.00");
        pay(key(), alice.getId(), bob.getId(), "2.00");

        mvc.perform(get("/api/accounts/" + bob.getId() + "/transactions?page=0&size=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(2.00))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void validationErrors() throws Exception {
        // missing header
        mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content(body(alice.getId(), bob.getId(), "10.00")))
                .andExpect(status().isBadRequest());
        // negative amount
        pay(key(), alice.getId(), bob.getId(), "-5.00").andExpect(status().isBadRequest());
        // same account
        pay(key(), alice.getId(), alice.getId(), "5.00").andExpect(status().isBadRequest());
        // unknown account
        pay(key(), alice.getId(), 999999L, "5.00").andExpect(status().isNotFound());
    }

    private ResultActions pay(String key, Long from, Long to, String amount) throws Exception {
        return mvc.perform(post("/api/payments")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(from, to, amount)));
    }

    private String body(Long from, Long to, String amount) throws Exception {
        return json.writeValueAsString(new PaymentRequest(from, to, new BigDecimal(amount)));
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }
}
