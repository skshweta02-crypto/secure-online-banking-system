package com.bank.service;

import com.bank.config.DatabaseConnectionFactory;
import com.bank.dao.AccountDAO;
import com.bank.dao.TransactionDAO;
import com.bank.dao.UserDAO;
import com.bank.exception.UnauthorizedAccessException;
import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.model.Transaction;
import com.bank.model.TransactionType;
import com.bank.model.User;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionServiceTest {

    private static final String H2_URL = "jdbc:h2:mem:secure_banking_tx_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static UserDAO userDAO;
    private static AccountDAO accountDAO;
    private static TransactionDAO transactionDAO;
    private static AccountService accountService;
    private static TransactionService transactionService;

    private User alice;
    private User bob;
    private Account aliceAccount;

    @BeforeAll
    static void setup() throws Exception {
        DatabaseConnectionFactory.setCredentials(H2_URL, "sa", "");
        userDAO = new UserDAO();
        accountDAO = new AccountDAO();
        transactionDAO = new TransactionDAO();
        accountService = new AccountService(accountDAO, transactionDAO);
        transactionService = new TransactionService(transactionDAO, accountDAO);

        try (Connection conn = DatabaseConnectionFactory.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "user_id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "full_name VARCHAR(100) NOT NULL, " +
                    "email VARCHAR(100) NOT NULL UNIQUE, " +
                    "password_hash VARCHAR(255) NOT NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            stmt.execute("CREATE TABLE IF NOT EXISTS accounts (" +
                    "account_id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "account_number VARCHAR(20) NOT NULL UNIQUE, " +
                    "user_id BIGINT NOT NULL, " +
                    "account_type VARCHAR(20) NOT NULL, " +
                    "balance DECIMAL(19, 2) NOT NULL DEFAULT 0.00, " +
                    "status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (user_id) REFERENCES users(user_id))");

            stmt.execute("CREATE TABLE IF NOT EXISTS transactions (" +
                    "transaction_id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "account_id BIGINT NOT NULL, " +
                    "transfer_id VARCHAR(64) NULL, " +
                    "transaction_type VARCHAR(20) NOT NULL, " +
                    "amount DECIMAL(19, 2) NOT NULL, " +
                    "description VARCHAR(255) NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (account_id) REFERENCES accounts(account_id))");
        }
    }

    @AfterAll
    static void tearDown() {
        DatabaseConnectionFactory.loadConfiguration();
    }

    @BeforeEach
    void resetData() throws Exception {
        try (Connection conn = DatabaseConnectionFactory.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM transactions");
            stmt.execute("DELETE FROM accounts");
            stmt.execute("DELETE FROM users");
        }

        alice = userDAO.create(new User("Alice Miller", "alice.m@test.com", "hash1"));
        bob = userDAO.create(new User("Bob Miller", "bob.m@test.com", "hash2"));

        aliceAccount = accountService.openAccount(alice.getUserId(), AccountType.SAVINGS, new BigDecimal("1000.00"));
        // Perform a deposit and withdrawal
        accountService.deposit(alice.getUserId(), aliceAccount.getAccountNumber(), new BigDecimal("200.00"));
        accountService.withdraw(alice.getUserId(), aliceAccount.getAccountNumber(), new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Retrieve complete statement for account owner")
    void testGetAccountStatement() throws Exception {
        List<Transaction> statement = transactionService.getAccountStatement(
                alice.getUserId(), aliceAccount.getAccountNumber());

        // Opening deposit ($1000) + deposit ($200) + withdrawal ($100) = 3
        assertEquals(3, statement.size());
    }

    @Test
    @DisplayName("Filter statement by transaction type")
    void testFilteredByType() throws Exception {
        List<Transaction> withdrawals = transactionService.getFilteredStatement(
                alice.getUserId(), aliceAccount.getAccountNumber(), TransactionType.WITHDRAWAL, null, null);

        assertEquals(1, withdrawals.size());
        assertEquals(TransactionType.WITHDRAWAL, withdrawals.get(0).getTransactionType());
        assertEquals(new BigDecimal("100.00"), withdrawals.get(0).getAmount());
    }

    @Test
    @DisplayName("Filter statement by date range")
    void testFilteredByDateRange() throws Exception {
        LocalDate today = LocalDate.now();
        List<Transaction> statement = transactionService.getFilteredStatement(
                alice.getUserId(), aliceAccount.getAccountNumber(), null, today.minusDays(1), today.plusDays(1));

        assertEquals(3, statement.size());
    }

    @Test
    @DisplayName("Statement request for another user's account throws UnauthorizedAccessException")
    void testUnauthorizedStatementAccess() {
        assertThrows(UnauthorizedAccessException.class, () ->
                transactionService.getAccountStatement(bob.getUserId(), aliceAccount.getAccountNumber()));
    }
}
