package com.bank.service;

import com.bank.config.DatabaseConnectionFactory;
import com.bank.dao.AccountDAO;
import com.bank.dao.TransactionDAO;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.BankingException;
import com.bank.exception.InsufficientBalanceException;
import com.bank.exception.UnauthorizedAccessException;
import com.bank.exception.ValidationException;
import com.bank.model.Account;
import com.bank.model.AccountStatus;
import com.bank.model.Transaction;
import com.bank.model.TransactionType;
import com.bank.model.TransferReceipt;
import com.bank.util.InputValidator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * High-priority fund transfer engine.
 * Ensures atomicity, deterministic lock acquisition order to prevent deadlocks,
 * strict post-lock balance revalidation, and dual ledger entries sharing a transfer ID.
 */
public class TransferService {

    private final AccountDAO accountDAO;
    private final TransactionDAO transactionDAO;

    public TransferService() {
        this(new AccountDAO(), new TransactionDAO());
    }

    public TransferService(AccountDAO accountDAO, TransactionDAO transactionDAO) {
        this.accountDAO = accountDAO;
        this.transactionDAO = transactionDAO;
    }

    /**
     * Executes an atomic transfer between two accounts.
     *
     * @param authenticatedUserId The ID of the authenticated user performing the transfer.
     * @param fromAccountNumber  The source account number owned by authenticated user.
     * @param toAccountNumber    The destination account number.
     * @param amount             Monetary amount to transfer.
     * @return TransferReceipt confirming execution and balances.
     */
    public TransferReceipt transferFunds(long authenticatedUserId, String fromAccountNumber,
                                         String toAccountNumber, BigDecimal amount)
            throws BankingException, SQLException {

        // 1. Initial Validation
        BigDecimal transferAmount = InputValidator.validatePositiveAmount(amount);
        InputValidator.validateAccountNumber(fromAccountNumber);
        InputValidator.validateAccountNumber(toAccountNumber);

        String normalizedFrom = fromAccountNumber.trim().toUpperCase();
        String normalizedTo = toAccountNumber.trim().toUpperCase();

        if (normalizedFrom.equalsIgnoreCase(normalizedTo)) {
            throw new ValidationException("Sender and receiver account numbers cannot be identical");
        }

        try (Connection conn = DatabaseConnectionFactory.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 2. Fetch both accounts to resolve IDs and verify ownership
                Account fromAccount = accountDAO.findByAccountNumber(conn, normalizedFrom)
                        .orElseThrow(() -> new AccountNotFoundException("account number", normalizedFrom));

                if (!fromAccount.getUserId().equals(authenticatedUserId)) {
                    throw new UnauthorizedAccessException(authenticatedUserId, normalizedFrom);
                }

                Account toAccount = accountDAO.findByAccountNumber(conn, normalizedTo)
                        .orElseThrow(() -> new AccountNotFoundException("account number", normalizedTo));

                // 3. Deterministic Lock Acquisition Order (Prevents Deadlocks)
                // Always acquire row locks in increasing order of primary key (account_id)
                long firstLockId = Math.min(fromAccount.getAccountId(), toAccount.getAccountId());
                long secondLockId = Math.max(fromAccount.getAccountId(), toAccount.getAccountId());

                Account firstLocked = accountDAO.findByIdForUpdate(conn, firstLockId)
                        .orElseThrow(() -> new AccountNotFoundException("account id", String.valueOf(firstLockId)));
                Account secondLocked = accountDAO.findByIdForUpdate(conn, secondLockId)
                        .orElseThrow(() -> new AccountNotFoundException("account id", String.valueOf(secondLockId)));

                Account lockedFrom = firstLocked.getAccountId().equals(fromAccount.getAccountId()) ? firstLocked : secondLocked;
                Account lockedTo = firstLocked.getAccountId().equals(toAccount.getAccountId()) ? firstLocked : secondLocked;

                // 4. Validate Account Statuses
                if (lockedFrom.getStatus() != AccountStatus.ACTIVE) {
                    throw new ValidationException("Sender account is not active (Status: " + lockedFrom.getStatus() + ")");
                }
                if (lockedTo.getStatus() != AccountStatus.ACTIVE) {
                    throw new ValidationException("Receiver account is not active (Status: " + lockedTo.getStatus() + ")");
                }

                // 5. Post-Lock Balance Revalidation
                if (!lockedFrom.canWithdraw(transferAmount)) {
                    throw new InsufficientBalanceException(lockedFrom.getBalance(), transferAmount);
                }

                // 6. Calculate New Balances
                BigDecimal newSenderBalance = lockedFrom.getBalance().subtract(transferAmount).setScale(2, RoundingMode.HALF_UP);
                BigDecimal newReceiverBalance = lockedTo.getBalance().add(transferAmount).setScale(2, RoundingMode.HALF_UP);

                // 7. Atomic Database Updates
                accountDAO.updateBalance(conn, lockedFrom.getAccountId(), newSenderBalance);
                accountDAO.updateBalance(conn, lockedTo.getAccountId(), newReceiverBalance);

                // 8. Shared Transfer ID for Dual Ledger Entries
                String transferId = "TXF-" + UUID.randomUUID().toString();
                LocalDateTime now = LocalDateTime.now();

                Transaction debitEntry = new Transaction(
                        lockedFrom.getAccountId(),
                        transferId,
                        TransactionType.TRANSFER_DEBIT,
                        transferAmount,
                        "Transfer to " + lockedTo.getAccountNumber(),
                        now
                );
                transactionDAO.create(conn, debitEntry);

                Transaction creditEntry = new Transaction(
                        lockedTo.getAccountId(),
                        transferId,
                        TransactionType.TRANSFER_CREDIT,
                        transferAmount,
                        "Transfer from " + lockedFrom.getAccountNumber(),
                        now
                );
                transactionDAO.create(conn, creditEntry);

                // 9. Atomic Commit
                conn.commit();

                // 10. Generate and Return Receipt
                return new TransferReceipt(
                        transferId,
                        lockedFrom.getAccountNumber(),
                        lockedTo.getAccountNumber(),
                        transferAmount,
                        newSenderBalance,
                        now
                );
            } catch (SQLException | BankingException e) {
                // Roll back any partial database operations on failure
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
}
