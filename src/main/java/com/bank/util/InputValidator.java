package com.bank.util;

import com.bank.exception.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * Validates domain input strings, email formats, password rules, and monetary values.
 */
public final class InputValidator {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,63}$");
    private static final Pattern ACCOUNT_NUMBER_PATTERN =
            Pattern.compile("^[A-Z0-9-]{6,20}$");
    private static final int MIN_PASSWORD_LENGTH = 8;

    private InputValidator() {
    }

    public static void validateFullName(String name) throws ValidationException {
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Full name is required");
        }
        String trimmed = name.trim();
        if (trimmed.length() < 2 || trimmed.length() > 100) {
            throw new ValidationException("Full name must be between 2 and 100 characters");
        }
        if (!trimmed.matches("^[\\p{L} .'-]+$")) {
            throw new ValidationException("Full name contains invalid characters");
        }
    }

    public static void validateEmail(String email) throws ValidationException {
        if (email == null || email.trim().isEmpty()) {
            throw new ValidationException("Email address is required");
        }
        String trimmed = email.trim().toLowerCase();
        if (trimmed.length() > 100 || !EMAIL_PATTERN.matcher(trimmed).matches()) {
            throw new ValidationException("Invalid email format (e.g. user@example.com)");
        }
    }

    public static void validatePassword(String password) throws ValidationException {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters long");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) hasLetter = true;
            if (Character.isDigit(c)) hasDigit = true;
        }
        if (!hasLetter || !hasDigit) {
            throw new ValidationException("Password must contain both letters and digits");
        }
    }

    public static BigDecimal parseAndValidateAmount(String amountStr) throws ValidationException {
        if (amountStr == null || amountStr.trim().isEmpty()) {
            throw new ValidationException("Amount cannot be empty");
        }
        try {
            BigDecimal amount = new BigDecimal(amountStr.trim());
            return validatePositiveAmount(amount);
        } catch (NumberFormatException e) {
            throw new ValidationException("Invalid numeric amount format: " + amountStr);
        }
    }

    public static BigDecimal validatePositiveAmount(BigDecimal amount) throws ValidationException {
        if (amount == null) {
            throw new ValidationException("Amount cannot be null");
        }
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        if (scaled.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Amount must be strictly greater than zero");
        }
        return scaled;
    }

    public static void validateAccountNumber(String accountNumber) throws ValidationException {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            throw new ValidationException("Account number is required");
        }
        String trimmed = accountNumber.trim().toUpperCase();
        if (!ACCOUNT_NUMBER_PATTERN.matcher(trimmed).matches()) {
            throw new ValidationException("Account number must be 6-20 alphanumeric characters (e.g. SA-100001)");
        }
    }
}
