package com.bank.model;

import com.bank.exception.InsufficientBalanceException;
import com.bank.exception.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Abstract base class representing a bank account.
 * Enforces monetary precision (BigDecimal), status rules, and polymorphism.
 */
public abstract class Account {

    protected Long accountId;
    protected String accountNumber;
    protected Long userId;
    protected AccountType accountType;
    protected BigDecimal balance;
    protected AccountStatus status;
    protected LocalDateTime createdAt;

    protected Account() {
        this.balance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.status = AccountStatus.ACTIVE;
        this.createdAt = LocalDateTime.now();
    }

    protected Account(Long accountId, String accountNumber, Long userId, AccountType accountType,
                      BigDecimal balance, AccountStatus status, LocalDateTime createdAt) {
        this.accountId = accountId;
        this.accountNumber = accountNumber;
        this.userId = userId;
        this.accountType = accountType;
        this.balance = (balance != null ? balance : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        this.status = status != null ? status : AccountStatus.ACTIVE;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    /**
     * Minimum balance required to be maintained in the account.
     */
    public abstract BigDecimal getMinimumBalance();

    /**
     * Polymorphic verification if a withdrawal of specified amount is permissible.
     */
    public abstract boolean canWithdraw(BigDecimal amount);

    public void deposit(BigDecimal amount) throws ValidationException {
        validatePositiveAmount(amount);
        validateActiveStatus();
        this.balance = this.balance.add(amount).setScale(2, RoundingMode.HALF_UP);
    }

    public void withdraw(BigDecimal amount) throws InsufficientBalanceException, ValidationException {
        validatePositiveAmount(amount);
        validateActiveStatus();
        if (!canWithdraw(amount)) {
            throw new InsufficientBalanceException(this.balance, amount);
        }
        this.balance = this.balance.subtract(amount).setScale(2, RoundingMode.HALF_UP);
    }

    protected void validatePositiveAmount(BigDecimal amount) throws ValidationException {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Transaction amount must be strictly greater than zero");
        }
    }

    protected void validateActiveStatus() throws ValidationException {
        if (this.status != AccountStatus.ACTIVE) {
            throw new ValidationException("Cannot perform transactions on an account with status: " + this.status);
        }
    }

    public boolean isActive() {
        return this.status == AccountStatus.ACTIVE;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public void setAccountType(AccountType accountType) {
        this.accountType = accountType;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = (balance != null ? balance : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account account)) return false;
        return Objects.equals(accountId, account.accountId) && Objects.equals(accountNumber, account.accountNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, accountNumber);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "accountId=" + accountId +
                ", accountNumber='" + accountNumber + '\'' +
                ", userId=" + userId +
                ", balance=" + balance +
                ", status=" + status +
                ", createdAt=" + createdAt +
                '}';
    }
}
