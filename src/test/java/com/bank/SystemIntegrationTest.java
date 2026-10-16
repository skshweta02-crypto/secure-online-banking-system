package com.bank;

import com.bank.config.DatabaseConnectionFactory;
import com.bank.dao.AccountDAO;
import com.bank.dao.TransactionDAO;
import com.bank.dao.UserDAO;
import com.bank.exception.InsufficientBalanceException;
import com.bank.exception.UnauthorizedAccessException;
import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.model.Transaction;
import com.bank.model.TransactionType;
import com.bank.model.TransferReceipt;
import com.bank.model.User;
import com.bank.service.AccountService;
import com.bank.service.AuthService;
import com.bank.service.TransactionService;
import com.bank.service.TransferService;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemIntegrationTest {

    private static final String H2_URL = "jdbc:h2:mem:secure_banking_e2e_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static UserDAO userDAO;
    private static AccountDAO accountDAO;
    private static TransactionDAO transactionDAO;
    private static AuthService authService;
    private static AccountService accountService;
    private static TransferService transferService;
    private static TransactionService transactionService;

    @BeforeAll
    static void initDatabase() throws Exception {
        DatabaseConnectionFactory.setCredentials(H2_URL, "sa", "");
        userDAO = new UserDAO();
        accountDAO = new AccountDAO();
        transactionDAO = new TransactionDAO();
        authService = new AuthService(userDAO);
        accountService = new AccountService(accountDAO, transactionDAO);
        transferService = new TransferService(accountDAO, transactionDAO);
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
    void resetTables() throws SQLException {
        try (Connection conn = DatabaseConnectionFactory.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM transactions");
            stmt.execute("DELETE FROM accounts");
            stmt.execute("DELETE FROM users");
        }
    }

    @Test
    @DisplayName("End-to-End User Journey: Register, Login, Account Openings, Deposit, Transfer, and Statement Audit")
    void testCompleteUserLifecycle() throws Exception {
        // 1. Register Users
        User alice = authService.register("Alice Wonderland", "alice@wonder.com", "Password123");
        User bob = authService.register("Bob Builder", "bob@builder.com", "SecureBob456");

        // 2. Authenticate
        User authAlice = authService.authenticate("alice@wonder.com", "Password123");
        assertEquals(alice.getUserId(), authAlice.getUserId());

        // 3. Alice opens Savings ($1000) and Current ($200) accounts
        Account aliceSavings = accountService.openAccount(alice.getUserId(), AccountType.SAVINGS, new BigDecimal("1000.00"));
        Account aliceCurrent = accountService.openAccount(alice.getUserId(), AccountType.CURRENT, new BigDecimal("200.00"));

        // Bob opens Current account ($300)
        Account bobCurrent = accountService.openAccount(bob.getUserId(), AccountType.CURRENT, new BigDecimal("300.00"));

        // 4. Verify account listing
        List<Account> aliceAccounts = accountService.getUserAccounts(alice.getUserId());
        assertEquals(2, aliceAccounts.size());

        // 5. Alice deposits $150 into her Current account
        accountService.deposit(alice.getUserId(), aliceCurrent.getAccountNumber(), new BigDecimal("150.00"));
        Account updatedCurrent = accountDAO.findById(aliceCurrent.getAccountId()).orElseThrow();
        assertEquals(new BigDecimal("350.00"), updatedCurrent.getBalance());

        // 6. Alice transfers $250 from Savings to Bob's Current account
        TransferReceipt receipt = transferService.transferFunds(
                alice.getUserId(),
                aliceSavings.getAccountNumber(),
                bobCurrent.getAccountNumber(),
                new BigDecimal("250.00")
        );
        assertNotNull(receipt.getTransferId());
        assertEquals(new BigDecimal("750.00"), receipt.getSenderRemainingBalance());

        // Verify Bob's new balance: 300 + 250 = 550.00
        Account updatedBob = accountDAO.findById(bobCurrent.getAccountId()).orElseThrow();
        assertEquals(new BigDecimal("550.00"), updatedBob.getBalance());

        // 7. Verify Statements and Dual Ledger Consistency
        List<Transaction> aliceSavingsStatement = transactionService.getAccountStatement(
                alice.getUserId(), aliceSavings.getAccountNumber());
        assertEquals(2, aliceSavingsStatement.size(), "Opening deposit + Transfer debit");

        List<Transaction> bobStatement = transactionService.getAccountStatement(
                bob.getUserId(), bobCurrent.getAccountNumber());
        assertEquals(2, bobStatement.size(), "Opening deposit + Transfer credit");

        Transaction aliceDebit = aliceSavingsStatement.stream()
                .filter(t -> t.getTransactionType() == TransactionType.TRANSFER_DEBIT)
                .findFirst().orElseThrow();
        Transaction bobCredit = bobStatement.stream()
                .filter(t -> t.getTransactionType() == TransactionType.TRANSFER_CREDIT)
                .findFirst().orElseThrow();

        assertEquals(receipt.getTransferId(), aliceDebit.getTransferId());
        assertEquals(receipt.getTransferId(), bobCredit.getTransferId());
        assertEquals(new BigDecimal("250.00"), aliceDebit.getAmount());
        assertEquals(new BigDecimal("250.00"), bobCredit.getAmount());
    }

    @Test
    @DisplayName("Transactional Conservation of Money: Concurrent cross-transfers maintain aggregate system balance")
    void testConservationOfMoneyUnderConcurrency() throws Exception {
        User userA = authService.register("User Alpha", "alpha@test.com", "PasswordA1");
        User userB = authService.register("User Beta", "beta@test.com", "PasswordB1");

        // User A starts with $1,000.00, User B starts with $1,000.00 (Current Accounts)
        Account accA = accountService.openAccount(userA.getUserId(), AccountType.CURRENT, new BigDecimal("1000.00"));
        Account accB = accountService.openAccount(userB.getUserId(), AccountType.CURRENT, new BigDecimal("1000.00"));

        BigDecimal initialTotalSystemMoney = accA.getBalance().add(accB.getBalance()); // $2,000.00

        int iterations = 20;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(iterations);

        for (int i = 0; i < iterations; i++) {
            final boolean aToB = (i % 2 == 0);
            executor.submit(() -> {
                try {
                    latch.await();
                    if (aToB) {
                        transferService.transferFunds(userA.getUserId(), accA.getAccountNumber(), accB.getAccountNumber(), new BigDecimal("50.00"));
                    } else {
                        transferService.transferFunds(userB.getUserId(), accB.getAccountNumber(), accA.getAccountNumber(), new BigDecimal("30.00"));
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        latch.countDown();
        doneLatch.await();
        executor.shutdown();

        Account finalAccA = accountDAO.findById(accA.getAccountId()).orElseThrow();
        Account finalAccB = accountDAO.findById(accB.getAccountId()).orElseThrow();

        BigDecimal finalTotalSystemMoney = finalAccA.getBalance().add(finalAccB.getBalance());
        assertEquals(initialTotalSystemMoney, finalTotalSystemMoney,
                "Total money in system must remain invariant regardless of concurrency!");
    }

    @Test
    @DisplayName("Security Boundary: Non-owners are completely blocked from reading or withdrawing from foreign accounts")
    void testSecurityBoundaries() throws Exception {
        User alice = authService.register("Alice Security", "alice.sec@test.com", "PasswordA1");
        User eve = authService.register("Eve Attacker", "eve.sec@test.com", "PasswordE1");

        Account aliceAcc = accountService.openAccount(alice.getUserId(), AccountType.SAVINGS, new BigDecimal("800.00"));

        // Eve cannot inspect Alice's balance
        assertThrows(UnauthorizedAccessException.class, () ->
                accountService.getAccountWithOwnershipCheck(eve.getUserId(), aliceAcc.getAccountNumber()));

        // Eve cannot withdraw from Alice's account
        assertThrows(UnauthorizedAccessException.class, () ->
                accountService.withdraw(eve.getUserId(), aliceAcc.getAccountNumber(), new BigDecimal("100.00")));

        // Eve cannot view Alice's statement
        assertThrows(UnauthorizedAccessException.class, () ->
                transactionService.getAccountStatement(eve.getUserId(), aliceAcc.getAccountNumber()));

        // Eve cannot transfer money from Alice's account
        Account eveAcc = accountService.openAccount(eve.getUserId(), AccountType.CURRENT, new BigDecimal("100.00"));
        assertThrows(UnauthorizedAccessException.class, () ->
                transferService.transferFunds(eve.getUserId(), aliceAcc.getAccountNumber(), eveAcc.getAccountNumber(), new BigDecimal("100.00")));

        // Alice's balance remains strictly untouched
        Account secureAlice = accountDAO.findById(aliceAcc.getAccountId()).orElseThrow();
        assertEquals(new BigDecimal("800.00"), secureAlice.getBalance());
    }
}
