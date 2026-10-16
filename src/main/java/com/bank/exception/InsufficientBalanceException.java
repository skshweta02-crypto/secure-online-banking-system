package com.bank.exception;

import java.math.BigDecimal;

/**
 * Thrown when an account does not have sufficient funds or violates minimum balance rules.
 */
public class InsufficientBalanceException extends BankingException {

    private final BigDecimal currentBalance;
    private final BigDecimal requestedAmount;

    public InsufficientBalanceException(String message) {
        super(message);
        this.currentBalance = null;
        this.requestedAmount = null;
    }

    public InsufficientBalanceException(BigDecimal currentBalance, BigDecimal requestedAmount) {
        super(String.format("Insufficient funds: current balance is %s, requested amount is %s",
                currentBalance != null ? currentBalance.toPlainString() : "0.00",
                requestedAmount != null ? requestedAmount.toPlainString() : "0.00"));
        this.currentBalance = currentBalance;
        this.requestedAmount = requestedAmount;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }
}
