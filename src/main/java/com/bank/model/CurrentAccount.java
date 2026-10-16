package com.bank.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Concrete model for Current Accounts.
 * Allows withdrawals down to $0.00 without a mandatory minimum reserve.
 */
public class CurrentAccount extends Account {

    public static final BigDecimal MINIMUM_BALANCE = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    public CurrentAccount() {
        super();
        this.accountType = AccountType.CURRENT;
    }

    public CurrentAccount(String accountNumber, Long userId, BigDecimal initialBalance) {
        super(null, accountNumber, userId, AccountType.CURRENT, initialBalance, AccountStatus.ACTIVE, LocalDateTime.now());
    }

    public CurrentAccount(Long accountId, String accountNumber, Long userId, BigDecimal balance,
                          AccountStatus status, LocalDateTime createdAt) {
        super(accountId, accountNumber, userId, AccountType.CURRENT, balance, status, createdAt);
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
