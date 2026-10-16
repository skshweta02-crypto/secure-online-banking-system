package com.bank.exception;

/**
 * Thrown when credentials fail or authentication state is invalid.
 */
public class AuthenticationException extends BankingException {

    public AuthenticationException(String message) {
        super(message);
    }
}
