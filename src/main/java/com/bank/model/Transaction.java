package com.bank.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Domain model representing an immutable financial transaction entry.
 */
public class Transaction {

    private Long transactionId;
    private Long accountId;
    private String transferId;
    private TransactionType transactionType;
    private BigDecimal amount;
    private String description;
    private LocalDateTime createdAt;

    public Transaction() {
    }

    public Transaction(Long accountId, String transferId, TransactionType transactionType,
                       BigDecimal amount, String description) {
        this(null, accountId, transferId, transactionType, amount, description, LocalDateTime.now());
    }

    public Transaction(Long accountId, String transferId, TransactionType transactionType,
                       BigDecimal amount, String description, LocalDateTime createdAt) {
        this(null, accountId, transferId, transactionType, amount, description, createdAt);
    }

    public Transaction(Long transactionId, Long accountId, String transferId, TransactionType transactionType,
                       BigDecimal amount, String description, LocalDateTime createdAt) {
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.transferId = transferId;
        this.transactionType = transactionType;
        this.amount = (amount != null ? amount : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        this.description = description;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = (amount != null ? amount : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
        if (!(o instanceof Transaction that)) return false;
        return Objects.equals(transactionId, that.transactionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionId);
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "transactionId=" + transactionId +
                ", accountId=" + accountId +
                ", transferId='" + transferId + '\'' +
                ", transactionType=" + transactionType +
                ", amount=" + amount +
                ", description='" + description + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
