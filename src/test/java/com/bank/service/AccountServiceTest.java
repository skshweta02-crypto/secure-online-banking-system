package com.bank.service;

import com.bank.config.DatabaseConnectionFactory;
import com.bank.dao.AccountDAO;
import com.bank.dao.TransactionDAO;
import com.bank.dao.UserDAO;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.InsufficientBalanceException;
import com.bank.exception.UnauthorizedAccessException;
import com.bank.exception.ValidationException;
import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.model.SavingsAccount;
import com.bank.model.Transaction;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountServiceTest {

    private static final String H2_URL = "jdbc:h2:mem:secure_banking_acc_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static UserDAO userDAO;
    private static AccountDAO accountDAO;
    private static TransactionDAO transactionDAO;
    private static AccountService accountService;

    private User userAlice;
    private User userBob;

    @BeforeAll
    static void setup() throws Exception {
        DatabaseConnectionFactory.setCredentials(H2_URL, "sa", "");
        userDAO = new UserDAO();
        accountDAO = new AccountDAO();
        transactionDAO = new TransactionDAO();
        accountService = new AccountService(accountDAO, transactionDAO);

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
    void resetData() throws SQLException {
        try (Connection conn = DatabaseConnectionFactory.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM transactions");
            stmt.execute("DELETE FROM accounts");
            stmt.execute("DELETE FROM users");
        }

        userAlice = userDAO.create(new User("Alice Wonder", "alice@example.com", "hashA"));
        userBob = userDAO.create(new User("Bob Builder", "bob@example.com", "hashB"));
    }

    @Test
    @DisplayName("Open savings account enforces initial minimum deposit of $500.00")
    void testOpenSavingsAccount() throws Exception {
        // Less than minimum throws ValidationException
        assertThrows(ValidationException.class, () ->
                accountService.openAccount(userAlice.getUserId(), AccountType.SAVINGS, new BigDecimal("499.99")));

        // Meets minimum requirement
        Account savings = accountService.openAccount(userAlice.getUserId(), AccountType.SAVINGS, new BigDecimal("600.00"));
        assertNotNull(savings.getAccountId());
        assertTrue(savings.getAccountNumber().startsWith("SA-"));
        assertEquals(new BigDecimal("600.00"), savings.getBalance());

        // Initial deposit recorded in ledger
        List<Transaction> txs = transactionDAO.findByAccountId(savings.getAccountId());
        assertEquals(1, txs.size());
        assertEquals(new BigDecimal("600.00"), txs.get(0).getAmount());
    }

    @Test
    @DisplayName("Open current account allows zero opening balance")
    void testOpenCurrentAccount() throws Exception {
        Account current = accountService.openAccount(userBob.getUserId(), AccountType.CURRENT, BigDecimal.ZERO);
        assertNotNull(current.getAccountId());
        assertTrue(current.getAccountNumber().startsWith("CA-"));
        assertEquals(BigDecimal.ZERO.setScale(2), current.getBalance());

        // No ledger entry created for 0 opening deposit
        List<Transaction> txs = transactionDAO.findByAccountId(current.getAccountId());
        assertEquals(0, txs.size());
    }

    @Test
    @DisplayName("Ownership check prevents unauthorized user access")
    void testOwnershipEnforcement() throws Exception {
        Account aliceAccount = accountService.openAccount(userAlice.getUserId(), AccountType.SAVINGS, new BigDecimal("1000.00"));

        // Alice accessing her own account succeeds
        Account fetched = accountService.getAccountWithOwnershipCheck(userAlice.getUserId(), aliceAccount.getAccountNumber());
        assertEquals(aliceAccount.getAccountId(), fetched.getAccountId());

        // Bob attempting to access Alice's account throws UnauthorizedAccessException
        assertThrows(UnauthorizedAccessException.class, () ->
                accountService.getAccountWithOwnershipCheck(userBob.getUserId(), aliceAccount.getAccountNumber()));

        // Non-existent account throws AccountNotFoundException
        assertThrows(AccountNotFoundException.class, () ->
                accountService.getAccountWithOwnershipCheck(userAlice.getUserId(), "SA-999999"));
    }

    @Test
    @DisplayName("Deposit updates balance atomically and records ledger entry")
    void testDepositWorkflow() throws Exception {
        Account account = accountService.openAccount(userAlice.getUserId(), AccountType.CURRENT, new BigDecimal("100.00"));

        Account updated = accountService.deposit(userAlice.getUserId(), account.getAccountNumber(), new BigDecimal("250.50"));
        assertEquals(new BigDecimal("350.50"), updated.getBalance());

        // Verify ledger entries (opening deposit + new deposit = 2)
        List<Transaction> txs = transactionDAO.findByAccountId(account.getAccountId());
        assertEquals(2, txs.size());

        // Non-owner cannot deposit
        assertThrows(UnauthorizedAccessException.class, () ->
                accountService.deposit(userBob.getUserId(), account.getAccountNumber(), new BigDecimal("50.00")));
    }

    @Test
    @DisplayName("Withdrawal enforces sufficient funds and minimum balance limits")
    void testWithdrawalWorkflow() throws Exception {
        Account savings = accountService.openAccount(userAlice.getUserId(), AccountType.SAVINGS, new BigDecimal("1000.00"));

        // Valid withdrawal leaving $600.00 (>= 500.00 minimum)
        Account afterWithdrawal = accountService.withdraw(userAlice.getUserId(), savings.getAccountNumber(), new BigDecimal("400.00"));
        assertEquals(new BigDecimal("600.00"), afterWithdrawal.getBalance());

        // Attempting to withdraw $200 would leave $400 (< $500 min balance) -> throws InsufficientBalanceException
        assertThrows(InsufficientBalanceException.class, () ->
                accountService.withdraw(userAlice.getUserId(), savings.getAccountNumber(), new BigDecimal("200.00")));

        // Balance remains unchanged at $600.00
        Account verified = accountService.getAccountWithOwnershipCheck(userAlice.getUserId(), savings.getAccountNumber());
        assertEquals(new BigDecimal("600.00"), verified.getBalance());

        // Non-owner cannot withdraw
        assertThrows(UnauthorizedAccessException.class, () ->
                accountService.withdraw(userBob.getUserId(), savings.getAccountNumber(), new BigDecimal("50.00")));
    }
}
