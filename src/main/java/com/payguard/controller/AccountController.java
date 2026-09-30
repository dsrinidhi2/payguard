package com.payguard.controller;

import com.payguard.dto.AccountResponse;
import com.payguard.dto.CreateAccountRequest;
import com.payguard.dto.PageResponse;
import com.payguard.dto.TransactionResponse;
import com.payguard.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "Open a new account")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
        return accountService.create(request);
    }

    @Operation(summary = "Get an account and its current balance")
    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable Long id) {
        return accountService.get(id);
    }

    @Operation(summary = "Paginated transaction history (sent and received), newest first")
    @GetMapping("/{id}/transactions")
    public PageResponse<TransactionResponse> history(@PathVariable Long id,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "10") int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        return accountService.history(id, Math.max(page, 0), safeSize);
    }
}
