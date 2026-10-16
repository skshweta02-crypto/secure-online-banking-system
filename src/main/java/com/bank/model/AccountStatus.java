package com.bank.model;

public enum AccountStatus {
    ACTIVE,
    SUSPENDED,
    CLOSED;

    public static AccountStatus fromString(String text) {
        if (text == null) return ACTIVE;
        for (AccountStatus status : values()) {
            if (status.name().equalsIgnoreCase(text.trim())) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown account status: " + text);
    }
}
