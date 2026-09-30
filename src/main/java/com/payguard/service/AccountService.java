package com.payguard.service;

import com.payguard.dto.AccountResponse;
import com.payguard.dto.CreateAccountRequest;
import com.payguard.dto.PageResponse;
import com.payguard.dto.TransactionResponse;
import com.payguard.exception.PayGuardException;
import com.payguard.model.Account;
import com.payguard.repository.AccountRepository;
import com.payguard.repository.TransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AccountService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        Account account = accountRepository.save(new Account(request.holderName(), request.openingBalance()));
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public AccountResponse get(Long id) {
        return AccountResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> history(Long accountId, int page, int size) {
        find(accountId); // 404 if the account doesn't exist
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return PageResponse.from(transactionRepository.findHistory(accountId, pageable).map(TransactionResponse::from));
    }

    private Account find(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> PayGuardException.notFound("Account " + id + " not found"));
    }
}
