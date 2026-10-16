package com.bank.model;

public enum TransactionType {
    DEPOSIT("Deposit", false),
    WITHDRAWAL("Withdrawal", true),
    TRANSFER_DEBIT("Transfer Sent", true),
    TRANSFER_CREDIT("Transfer Received", false);

    private final String displayName;
    private final boolean isDebit;

    TransactionType(String displayName, boolean isDebit) {
        this.displayName = displayName;
        this.isDebit = isDebit;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isDebit() {
        return isDebit;
    }

    public boolean isCredit() {
        return !isDebit;
    }

    public static TransactionType fromString(String text) {
        if (text == null) return null;
        for (TransactionType type : values()) {
            if (type.name().equalsIgnoreCase(text.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown transaction type: " + text);
    }
}
