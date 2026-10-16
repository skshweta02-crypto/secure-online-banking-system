package com.bank.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Concrete model for Savings Accounts.
 * Requires maintaining a minimum balance threshold (e.g., $500.00).
 */
public class SavingsAccount extends Account {

    public static final BigDecimal MINIMUM_BALANCE = new BigDecimal("500.00").setScale(2, RoundingMode.HALF_UP);

    public SavingsAccount() {
        super();
        this.accountType = AccountType.SAVINGS;
    }

    public SavingsAccount(String accountNumber, Long userId, BigDecimal initialBalance) {
        super(null, accountNumber, userId, AccountType.SAVINGS, initialBalance, AccountStatus.ACTIVE, LocalDateTime.now());
    }

    public SavingsAccount(Long accountId, String accountNumber, Long userId, BigDecimal balance,
                          AccountStatus status, LocalDateTime createdAt) {
        super(accountId, accountNumber, userId, AccountType.SAVINGS, balance, status, createdAt);
    }

    @Override
    public BigDecimal getMinimumBalance() {
        return MINIMUM_BALANCE;
    }

    @Override
    public boolean canWithdraw(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return this.balance.subtract(amount).compareTo(MINIMUM_BALANCE) >= 0;
    }
}
