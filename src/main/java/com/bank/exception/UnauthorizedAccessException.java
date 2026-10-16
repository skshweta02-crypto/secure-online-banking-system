package com.bank.exception;

/**
 * Thrown when an authenticated user attempts to access an account they do not own.
 */
public class UnauthorizedAccessException extends BankingException {

    public UnauthorizedAccessException(String message) {
        super(message);
    }

    public UnauthorizedAccessException(long userId, String accountNumber) {
        super(String.format("User %d is not authorized to access or operate on account %s", userId, accountNumber));
    }
}
