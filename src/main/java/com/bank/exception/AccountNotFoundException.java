package com.bank.exception;

/**
 * Thrown when an account cannot be located by ID or account number.
 */
public class AccountNotFoundException extends BankingException {

    public AccountNotFoundException(String message) {
        super(message);
    }

    public AccountNotFoundException(String identifierType, String identifier) {
        super(String.format("Account not found with %s: %s", identifierType, identifier));
    }
}
