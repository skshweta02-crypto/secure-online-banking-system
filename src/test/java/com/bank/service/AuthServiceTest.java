package com.bank.service;

import com.bank.config.DatabaseConnectionFactory;
import com.bank.dao.UserDAO;
import com.bank.exception.AuthenticationException;
import com.bank.exception.ValidationException;
import com.bank.model.User;
import com.bank.util.InputValidator;
import com.bank.util.PasswordUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {

    private static final String H2_URL = "jdbc:h2:mem:secure_banking_auth_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static UserDAO userDAO;
    private static AuthService authService;

    @BeforeAll
    static void setup() throws Exception {
        DatabaseConnectionFactory.setCredentials(H2_URL, "sa", "");
        userDAO = new UserDAO();
        authService = new AuthService(userDAO);

        try (Connection conn = DatabaseConnectionFactory.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "user_id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "full_name VARCHAR(100) NOT NULL, " +
                    "email VARCHAR(100) NOT NULL UNIQUE, " +
                    "password_hash VARCHAR(255) NOT NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        }
    }

    @AfterAll
    static void tearDown() {
        DatabaseConnectionFactory.loadConfiguration();
    }

    @BeforeEach
    void cleanUsers() throws SQLException {
        try (Connection conn = DatabaseConnectionFactory.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM users");
        }
    }

    @Test
    @DisplayName("PasswordUtil produces unique salts and verifies valid passwords")
    void testPasswordUtil() {
        String plain = "SecurePass123";
        String hash1 = PasswordUtil.hashPassword(plain);
        String hash2 = PasswordUtil.hashPassword(plain);

        assertTrue(hash1.startsWith("$2a$12$"));
        assertNotEquals(hash1, hash2, "BCrypt salts should produce unique hashes for the same password");

        assertTrue(PasswordUtil.checkPassword(plain, hash1));
        assertTrue(PasswordUtil.checkPassword(plain, hash2));
        assertFalse(PasswordUtil.checkPassword("WrongPass123", hash1));
    }

    @Test
    @DisplayName("InputValidator enforces format rules")
    void testInputValidator() {
        assertDoesNotThrow(() -> InputValidator.validateFullName("John Doe"));
        assertThrows(ValidationException.class, () -> InputValidator.validateFullName(""));
        assertThrows(ValidationException.class, () -> InputValidator.validateFullName("12345"));

        assertDoesNotThrow(() -> InputValidator.validateEmail("john.doe@bank.com"));
        assertThrows(ValidationException.class, () -> InputValidator.validateEmail("invalid-email"));

        assertDoesNotThrow(() -> InputValidator.validatePassword("Pass1234"));
        assertThrows(ValidationException.class, () -> InputValidator.validatePassword("short"));
        assertThrows(ValidationException.class, () -> InputValidator.validatePassword("nouppercaseordigits"));
    }

    @Test
    @DisplayName("UserSession manages authenticated state lifecycle")
    void testUserSession() {
        UserSession session = new UserSession();
        assertFalse(session.isLoggedIn());
        assertNull(session.getCurrentUser());

        User user = new User(10L, "Jane Doe", "jane@test.com", "hash", null);
        session.login(user);

        assertTrue(session.isLoggedIn());
        assertEquals(10L, session.getCurrentUserId());
        assertNotNull(session.getLoginTime());

        session.logout();
        assertFalse(session.isLoggedIn());
        assertNull(session.getCurrentUser());
    }

    @Test
    @DisplayName("AuthService register and authenticate workflow")
    void testRegisterAndAuthenticate() throws Exception {
        User registered = authService.register("John Doe", "john@test.com", "SecurePassword1");
        assertNotNull(registered.getUserId());
        assertEquals("john@test.com", registered.getEmail());
        assertTrue(registered.getPasswordHash().startsWith("$2a$12$"));

        // Login with correct credentials
        User loggedIn = authService.authenticate("john@test.com", "SecurePassword1");
        assertEquals(registered.getUserId(), loggedIn.getUserId());

        // Login with wrong password
        assertThrows(AuthenticationException.class, () ->
                authService.authenticate("john@test.com", "WrongPassword1"));

        // Login with non-existent email
        assertThrows(AuthenticationException.class, () ->
                authService.authenticate("nobody@test.com", "SecurePassword1"));

        // Duplicate registration fails
        assertThrows(ValidationException.class, () ->
                authService.register("John Clone", "john@test.com", "AnotherPassword2"));
    }
}
