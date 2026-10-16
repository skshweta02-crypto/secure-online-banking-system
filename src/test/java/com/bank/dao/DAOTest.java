package com.bank.dao;

import com.bank.config.DatabaseConnectionFactory;
import com.bank.model.Account;
import com.bank.model.AccountStatus;
import com.bank.model.AccountType;
import com.bank.model.CurrentAccount;
import com.bank.model.SavingsAccount;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DAOTest {

    private static final String H2_URL = "jdbc:h2:mem:secure_banking_dao_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static UserDAO userDAO;
    private static AccountDAO accountDAO;
    private static TransactionDAO transactionDAO;

    @BeforeAll
    static void setupDatabase() throws Exception {
        DatabaseConnectionFactory.setCredentials(H2_URL, "sa", "");
        userDAO = new UserDAO();
        accountDAO = new AccountDAO();
        transactionDAO = new TransactionDAO();

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
    void cleanTables() throws SQLException {
        try (Connection conn = DatabaseConnectionFactory.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM transactions");
            stmt.execute("DELETE FROM accounts");
            stmt.execute("DELETE FROM users");
        }
    }

    @Test
    @DisplayName("UserDAO CRUD and unique email validation")
    void testUserDAOCrud() throws SQLException {
        User user = new User("Alice Green", "alice.green@test.com", "hash123");
        User created = userDAO.create(user);

        assertNotNull(created.getUserId());
        assertTrue(created.getUserId() > 0);

        Optional<User> byId = userDAO.findById(created.getUserId());
        assertTrue(byId.isPresent());
        assertEquals("Alice Green", byId.get().getFullName());
        assertEquals("alice.green@test.com", byId.get().getEmail());

        Optional<User> byEmail = userDAO.findByEmail("alice.green@test.com");
        assertTrue(byEmail.isPresent());
        assertEquals(created.getUserId(), byEmail.get().getUserId());

        assertTrue(userDAO.existsByEmail("alice.green@test.com"));
        assertFalse(userDAO.existsByEmail("nonexistent@test.com"));

        // Duplicate email throws SQLException
        User duplicate = new User("Alice Duplicate", "alice.green@test.com", "hash456");
        assertThrows(SQLException.class, () -> userDAO.create(duplicate));
    }

    @Test
    @DisplayName("AccountDAO polymorphic mapping, row locking and atomic balance update")
    void testAccountDAOCrudAndLocking() throws SQLException {
        User user = userDAO.create(new User("Bob Brown", "bob@test.com", "hash123"));

        SavingsAccount savings = new SavingsAccount("SA-9001", user.getUserId(), new BigDecimal("1500.00"));
        Account createdSavings = accountDAO.create(savings);
        assertNotNull(createdSavings.getAccountId());
        assertTrue(createdSavings instanceof SavingsAccount);

        CurrentAccount current = new CurrentAccount("CA-9002", user.getUserId(), new BigDecimal("800.00"));
        Account createdCurrent = accountDAO.create(current);
        assertNotNull(createdCurrent.getAccountId());
        assertTrue(createdCurrent instanceof CurrentAccount);

        // Find by user ID returns both accounts
        List<Account> userAccounts = accountDAO.findByUserId(user.getUserId());
        assertEquals(2, userAccounts.size());

        // SELECT ... FOR UPDATE in transactional connection
        try (Connection txConn = DatabaseConnectionFactory.getConnection()) {
            txConn.setAutoCommit(false);
            Optional<Account> locked = accountDAO.findByIdForUpdate(txConn, createdSavings.getAccountId());
            assertTrue(locked.isPresent());
            assertEquals(new BigDecimal("1500.00"), locked.get().getBalance());

            // Atomic balance update
            accountDAO.updateBalance(txConn, createdSavings.getAccountId(), new BigDecimal("2000.00"));
            txConn.commit();
        }

        // Verify balance committed
        Optional<Account> updated = accountDAO.findById(createdSavings.getAccountId());
        assertTrue(updated.isPresent());
        assertEquals(new BigDecimal("2000.00"), updated.get().getBalance());
    }

    @Test
    @DisplayName("TransactionDAO ledger recording and statement filtering")
    void testTransactionDAOCrud() throws SQLException {
        User user = userDAO.create(new User("Charlie White", "charlie@test.com", "hash123"));
        Account account = accountDAO.create(new CurrentAccount("CA-9003", user.getUserId(), new BigDecimal("1000.00")));

        String transferId = UUID.randomUUID().toString();
        Transaction debit = new Transaction(account.getAccountId(), transferId, TransactionType.TRANSFER_DEBIT,
                new BigDecimal("200.00"), "Transfer to Dave");
        Transaction createdDebit = transactionDAO.create(debit);
        assertNotNull(createdDebit.getTransactionId());

        Transaction deposit = new Transaction(account.getAccountId(), null, TransactionType.DEPOSIT,
                new BigDecimal("500.00"), "Salary deposit");
        transactionDAO.create(deposit);

        List<Transaction> all = transactionDAO.findByAccountId(account.getAccountId());
        assertEquals(2, all.size());

        // Find by transferId
        List<Transaction> byTransfer = transactionDAO.findByTransferId(transferId);
        assertEquals(1, byTransfer.size());
        assertEquals(TransactionType.TRANSFER_DEBIT, byTransfer.get(0).getTransactionType());

        // Filtered search by type
        List<Transaction> depositsOnly = transactionDAO.findFiltered(account.getAccountId(),
                TransactionType.DEPOSIT, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        assertEquals(1, depositsOnly.size());
        assertEquals(new BigDecimal("500.00"), depositsOnly.get(0).getAmount());
    }
}
