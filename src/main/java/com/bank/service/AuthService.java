package com.bank.service;

import com.bank.dao.UserDAO;
import com.bank.exception.AuthenticationException;
import com.bank.exception.BankingException;
import com.bank.exception.ValidationException;
import com.bank.model.User;
import com.bank.util.InputValidator;
import com.bank.util.PasswordUtil;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Service orchestrating user registration, credential verification, and authentication.
 */
public class AuthService {

    private final UserDAO userDAO;

    public AuthService() {
        this(new UserDAO());
    }

    public AuthService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    /**
     * Registers a new user with input validation, duplicate email check, and BCrypt hashing.
     */
    public User register(String fullName, String email, String plainPassword)
            throws BankingException, SQLException {

        InputValidator.validateFullName(fullName);
        InputValidator.validateEmail(email);
        InputValidator.validatePassword(plainPassword);

        String normalizedEmail = email.trim().toLowerCase();

        if (userDAO.existsByEmail(normalizedEmail)) {
            throw new ValidationException("An account with this email address already exists");
        }

        String passwordHash = PasswordUtil.hashPassword(plainPassword);
        User user = new User(fullName.trim(), normalizedEmail, passwordHash);

        return userDAO.create(user);
    }

    /**
     * Authenticates a user with email and plain password against stored BCrypt hash.
     */
    public User authenticate(String email, String plainPassword)
            throws AuthenticationException, ValidationException, SQLException {

        if (email == null || email.trim().isEmpty()) {
            throw new ValidationException("Email address is required for login");
        }
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new ValidationException("Password is required for login");
        }

        String normalizedEmail = email.trim().toLowerCase();
        Optional<User> userOpt = userDAO.findByEmail(normalizedEmail);

        if (userOpt.isEmpty()) {
            // Generic message prevents user enumeration attacks
            throw new AuthenticationException("Invalid email or password");
        }

        User user = userOpt.get();
        if (!PasswordUtil.checkPassword(plainPassword, user.getPasswordHash())) {
            throw new AuthenticationException("Invalid email or password");
        }

        return user;
    }
}
