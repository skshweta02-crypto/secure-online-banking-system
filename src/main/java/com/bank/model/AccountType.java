package com.bank.model;

public enum AccountType {
    SAVINGS("Savings Account"),
    CURRENT("Current Account");

    private final String displayName;

    AccountType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static AccountType fromString(String text) {
        if (text == null) return null;
        for (AccountType type : values()) {
            if (type.name().equalsIgnoreCase(text.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown account type: " + text);
    }
}
