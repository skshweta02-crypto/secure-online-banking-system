package com.bank.exception;

/**
 * Thrown when user or system input fails validation rules.
 */
public class ValidationException extends BankingException {

    public ValidationException(String message) {
        super(message);
    }
}
