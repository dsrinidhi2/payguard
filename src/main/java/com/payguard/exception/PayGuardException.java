package com.payguard.exception;

import org.springframework.http.HttpStatus;

/** Base class: every business error carries the HTTP status it should map to. */
public class PayGuardException extends RuntimeException {

    private final HttpStatus status;

    public PayGuardException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static PayGuardException notFound(String message) {
        return new PayGuardException(HttpStatus.NOT_FOUND, message);
    }

    public static PayGuardException badRequest(String message) {
        return new PayGuardException(HttpStatus.BAD_REQUEST, message);
    }

    public static PayGuardException insufficientFunds(Long accountId) {
        return new PayGuardException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Insufficient funds in account " + accountId);
    }

    public static PayGuardException idempotencyConflict(String key) {
        return new PayGuardException(HttpStatus.CONFLICT,
                "Idempotency-Key '" + key + "' was already used for a different payment");
    }

    public static PayGuardException tooMuchContention() {
        return new PayGuardException(HttpStatus.SERVICE_UNAVAILABLE,
                "Account is busy with other payments, please retry");
    }
}
