package com.bank.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Value object representing an executed, committed fund transfer confirmation.
 */
public class TransferReceipt {

    private final String transferId;
    private final String fromAccountNumber;
    private final String toAccountNumber;
    private final BigDecimal amount;
    private final BigDecimal senderRemainingBalance;
    private final LocalDateTime timestamp;

    public TransferReceipt(String transferId, String fromAccountNumber, String toAccountNumber,
                           BigDecimal amount, BigDecimal senderRemainingBalance, LocalDateTime timestamp) {
        this.transferId = transferId;
        this.fromAccountNumber = fromAccountNumber;
        this.toAccountNumber = toAccountNumber;
        this.amount = amount;
        this.senderRemainingBalance = senderRemainingBalance;
        this.timestamp = timestamp;
    }

    public String getTransferId() {
        return transferId;
    }

    public String getFromAccountNumber() {
        return fromAccountNumber;
    }

    public String getToAccountNumber() {
        return toAccountNumber;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getSenderRemainingBalance() {
        return senderRemainingBalance;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "TransferReceipt{" +
                "transferId='" + transferId + '\'' +
                ", from='" + fromAccountNumber + '\'' +
                ", to='" + toAccountNumber + '\'' +
                ", amount=" + amount +
                ", senderRemainingBalance=" + senderRemainingBalance +
                ", timestamp=" + timestamp +
                '}';
    }
}
