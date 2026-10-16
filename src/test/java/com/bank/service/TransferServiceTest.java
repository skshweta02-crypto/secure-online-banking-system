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
import com.bank.model.Transaction;
import com.bank.model.TransactionType;
import com.bank.model.TransferReceipt;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferServiceTest {

    private static final String H2_URL = "jdbc:h2:mem:secure_banking_transfer_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static UserDAO userDAO;
    private static AccountDAO accountDAO;
    private static TransactionDAO transactionDAO;
    private static AccountService accountService;
    private static TransferService transferService;

    private User alice;
    private User bob;
    private Account aliceSavings;
    private Account bobCurrent;

    @BeforeAll
    static void setup() throws Exception {
        DatabaseConnectionFactory.setCredentials(H2_URL, "sa", "");
        userDAO = new UserDAO();
        accountDAO = new AccountDAO();
        transactionDAO = new TransactionDAO();
        accountService = new AccountService(accountDAO, transactionDAO);
        transferService = new TransferService(accountDAO, transactionDAO);

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

        alice = userDAO.create(new User("Alice Smith", "alice@transfer.test", "hash1"));
        bob = userDAO.create(new User("Bob Jones", "bob@transfer.test", "hash2"));

        // Alice starts with $1,000.00 in Savings
        aliceSavings = accountService.openAccount(alice.getUserId(), AccountType.SAVINGS, new BigDecimal("1000.00"));
        // Bob starts with $200.00 in Current
        bobCurrent = accountService.openAccount(bob.getUserId(), AccountType.CURRENT, new BigDecimal("200.00"));
    }

    @Test
    @DisplayName("Successful fund transfer debits sender, credits receiver and creates dual ledger entries")
    void testSuccessfulTransfer() throws Exception {
        TransferReceipt receipt = transferService.transferFunds(
                alice.getUserId(),
                aliceSavings.getAccountNumber(),
                bobCurrent.getAccountNumber(),
                new BigDecimal("300.00")
        );

        assertNotNull(receipt.getTransferId());
        assertTrue(receipt.getTransferId().startsWith("TXF-"));
        assertEquals(new BigDecimal("700.00"), receipt.getSenderRemainingBalance());

        // Verify sender and receiver balances in DB
        Account updatedAlice = accountDAO.findById(aliceSavings.getAccountId()).orElseThrow();
        Account updatedBob = accountDAO.findById(bobCurrent.getAccountId()).orElseThrow();
        assertEquals(new BigDecimal("700.00"), updatedAlice.getBalance());
        assertEquals(new BigDecimal("500.00"), updatedBob.getBalance());

        // Verify paired ledger records sharing transferId
        List<Transaction> sharedTxs = transactionDAO.findByTransferId(receipt.getTransferId());
        assertEquals(2, sharedTxs.size());

        Transaction debitTx = sharedTxs.stream()
                .filter(t -> t.getTransactionType() == TransactionType.TRANSFER_DEBIT)
                .findFirst().orElseThrow();
        assertEquals(aliceSavings.getAccountId(), debitTx.getAccountId());
        assertEquals(new BigDecimal("300.00"), debitTx.getAmount());

        Transaction creditTx = sharedTxs.stream()
                .filter(t -> t.getTransactionType() == TransactionType.TRANSFER_CREDIT)
                .findFirst().orElseThrow();
        assertEquals(bobCurrent.getAccountId(), creditTx.getAccountId());
        assertEquals(new BigDecimal("300.00"), creditTx.getAmount());
    }

    @Test
    @DisplayName("Insufficient balance rolls back all database operations")
    void testInsufficientBalanceRollback() throws Exception {
        // Alice has $1,000.00. Attempting to transfer $1,200.00 throws InsufficientBalanceException
        assertThrows(InsufficientBalanceException.class, () ->
                transferService.transferFunds(
                        alice.getUserId(),
                        aliceSavings.getAccountNumber(),
                        bobCurrent.getAccountNumber(),
                        new BigDecimal("1200.00")
                ));

        // Verify balances remain completely unchanged
        Account verifiedAlice = accountDAO.findById(aliceSavings.getAccountId()).orElseThrow();
        Account verifiedBob = accountDAO.findById(bobCurrent.getAccountId()).orElseThrow();
        assertEquals(new BigDecimal("1000.00"), verifiedAlice.getBalance());
        assertEquals(new BigDecimal("200.00"), verifiedBob.getBalance());
    }

    @Test
    @DisplayName("Savings minimum balance ($500.00) rule enforcement during transfer")
    void testSavingsMinimumBalanceEnforcement() throws Exception {
        // Alice has $1,000.00 in Savings. Transferring $600.00 would leave $400 (< $500 min).
        assertThrows(InsufficientBalanceException.class, () ->
                transferService.transferFunds(
                        alice.getUserId(),
                        aliceSavings.getAccountNumber(),
                        bobCurrent.getAccountNumber(),
                        new BigDecimal("600.00")
                ));

        Account verifiedAlice = accountDAO.findById(aliceSavings.getAccountId()).orElseThrow();
        assertEquals(new BigDecimal("1000.00"), verifiedAlice.getBalance());
    }

    @Test
    @DisplayName("Unauthorized transfer attempt from an account not owned by authenticated user")
    void testUnauthorizedTransfer() {
        // Bob attempting to transfer money out of Alice's account throws UnauthorizedAccessException
        assertThrows(UnauthorizedAccessException.class, () ->
                transferService.transferFunds(
                        bob.getUserId(),
                        aliceSavings.getAccountNumber(),
                        bobCurrent.getAccountNumber(),
                        new BigDecimal("100.00")
                ));
    }

    @Test
    @DisplayName("Transfer to same account is rejected")
    void testSameAccountTransferRejected() {
        assertThrows(ValidationException.class, () ->
                transferService.transferFunds(
                        alice.getUserId(),
                        aliceSavings.getAccountNumber(),
                        aliceSavings.getAccountNumber(),
                        new BigDecimal("50.00")
                ));
    }

    @Test
    @DisplayName("Transfer to non-existent account is rejected and rolls back")
    void testNonExistentAccountTransfer() {
        assertThrows(AccountNotFoundException.class, () ->
                transferService.transferFunds(
                        alice.getUserId(),
                        aliceSavings.getAccountNumber(),
                        "CA-999999",
                        new BigDecimal("50.00")
                ));
    }

    @Test
    @DisplayName("Concurrent transfers prevent overspending and race conditions")
    void testConcurrentTransfersNoOverspending() throws Exception {
        // Alice starts with $1,000.00. Savings minimum balance is $500.00.
        // Therefore max total withdrawable amount is $500.00.
        // 10 concurrent threads each attempt to transfer $100.00.
        // Exactly 5 transfers should succeed ($500 spent, leaving $500), and 5 must fail!
        int totalThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < totalThreads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    transferService.transferFunds(
                            alice.getUserId(),
                            aliceSavings.getAccountNumber(),
                            bobCurrent.getAccountNumber(),
                            new BigDecimal("100.00")
                    );
                    successCount.incrementAndGet();
                } catch (InsufficientBalanceException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Trigger all concurrent threads simultaneously
        latch.countDown();
        doneLatch.await();
        executor.shutdown();

        Account finalAlice = accountDAO.findById(aliceSavings.getAccountId()).orElseThrow();
        Account finalBob = accountDAO.findById(bobCurrent.getAccountId()).orElseThrow();

        assertEquals(5, successCount.get(), "Exactly 5 transfers of $100 should succeed before hitting $500 min balance");
        assertEquals(5, failureCount.get(), "Remaining 5 transfers must be rejected due to insufficient balance");
        assertEquals(new BigDecimal("500.00"), finalAlice.getBalance(), "Alice's balance must be exactly $500.00 (min balance)");
        assertEquals(new BigDecimal("700.00"), finalBob.getBalance(), "Bob's balance must be $200 + $500 = $700.00");
    }
}
