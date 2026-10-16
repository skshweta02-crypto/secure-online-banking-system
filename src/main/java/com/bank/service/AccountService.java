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
import com.bank.model.AccountType;
import com.bank.model.CurrentAccount;
import com.bank.model.SavingsAccount;
import com.bank.model.Transaction;
import com.bank.model.TransactionType;
import com.bank.util.InputValidator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Service managing bank account lifecycle, ownership authorization,
 * atomic balance adjustments, and transaction ledger recording.
 */
public class AccountService {

    private final AccountDAO accountDAO;
    private final TransactionDAO transactionDAO;
    private final SecureRandom random = new SecureRandom();

    public AccountService() {
        this(new AccountDAO(), new TransactionDAO());
    }

    public AccountService(AccountDAO accountDAO, TransactionDAO transactionDAO) {
        this.accountDAO = accountDAO;
        this.transactionDAO = transactionDAO;
    }

    /**
     * Opens a new Savings or Current account for the authenticated user.
     * Enforces minimum opening balance and records initial deposit ledger entry if > 0.
     */
    public Account openAccount(long userId, AccountType type, BigDecimal initialDeposit)
            throws BankingException, SQLException {

        if (type == null) {
            throw new ValidationException("Account type must be specified");
        }

        BigDecimal deposit = initialDeposit != null
                ? initialDeposit.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        if (deposit.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Initial deposit cannot be negative");
        }

        if (type == AccountType.SAVINGS && deposit.compareTo(SavingsAccount.MINIMUM_BALANCE) < 0) {
            throw new ValidationException(String.format(
                    "Savings account opening requires a minimum initial deposit of $%s",
                    SavingsAccount.MINIMUM_BALANCE.toPlainString()));
        }

        String accountNumber = generateUniqueAccountNumber(type);

        Account account;
        if (type == AccountType.SAVINGS) {
            account = new SavingsAccount(accountNumber, userId, deposit);
        } else {
            account = new CurrentAccount(accountNumber, userId, deposit);
        }

        try (Connection conn = DatabaseConnectionFactory.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Account created = accountDAO.create(conn, account);

                if (deposit.compareTo(BigDecimal.ZERO) > 0) {
                    Transaction initialTx = new Transaction(
                            created.getAccountId(),
                            null,
                            TransactionType.DEPOSIT,
                            deposit,
                            "Initial account opening deposit"
                    );
                    transactionDAO.create(conn, initialTx);
                }

                conn.commit();
                return created;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Retrieves all accounts belonging to the authenticated user.
     */
    public List<Account> getUserAccounts(long userId) throws SQLException {
        return accountDAO.findByUserId(userId);
    }

    /**
     * Finds an account by account number and strictly verifies user ownership.
     */
    public Account getAccountWithOwnershipCheck(long userId, String accountNumber)
            throws BankingException, SQLException {

        InputValidator.validateAccountNumber(accountNumber);

        Account account = accountDAO.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("account number", accountNumber));

        verifyOwnership(userId, account);
        return account;
    }

    /**
     * Executes an atomic deposit on the user's account with row-level locking and ledger creation.
     */
    public Account deposit(long userId, String accountNumber, BigDecimal amount)
            throws BankingException, SQLException {

        BigDecimal validatedAmount = InputValidator.validatePositiveAmount(amount);

        try (Connection conn = DatabaseConnectionFactory.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Find account first to get ID
                Account account = accountDAO.findByAccountNumber(conn, accountNumber)
                        .orElseThrow(() -> new AccountNotFoundException("account number", accountNumber));

                verifyOwnership(userId, account);

                // Acquire exclusive row lock
                Account locked = accountDAO.findByIdForUpdate(conn, account.getAccountId())
                        .orElseThrow(() -> new AccountNotFoundException("account id", String.valueOf(account.getAccountId())));

                if (locked.getStatus() != AccountStatus.ACTIVE) {
                    throw new ValidationException("Cannot deposit to account with status: " + locked.getStatus());
                }

                // Update balance
                BigDecimal newBalance = locked.getBalance().add(validatedAmount).setScale(2, RoundingMode.HALF_UP);
                accountDAO.updateBalance(conn, locked.getAccountId(), newBalance);
                locked.setBalance(newBalance);

                // Ledger transaction
                Transaction tx = new Transaction(
                        locked.getAccountId(),
                        null,
                        TransactionType.DEPOSIT,
                        validatedAmount,
                        "Cash / Self deposit"
                );
                transactionDAO.create(conn, tx);

                conn.commit();
                return locked;
            } catch (SQLException | BankingException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Executes an atomic withdrawal from the user's account with row-level locking,
     * balance re-validation, and ledger creation.
     */
    public Account withdraw(long userId, String accountNumber, BigDecimal amount)
            throws BankingException, SQLException {

        BigDecimal validatedAmount = InputValidator.validatePositiveAmount(amount);

        try (Connection conn = DatabaseConnectionFactory.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Find account to get ID
                Account account = accountDAO.findByAccountNumber(conn, accountNumber)
                        .orElseThrow(() -> new AccountNotFoundException("account number", accountNumber));

                verifyOwnership(userId, account);

                // Acquire exclusive row lock
                Account locked = accountDAO.findByIdForUpdate(conn, account.getAccountId())
                        .orElseThrow(() -> new AccountNotFoundException("account id", String.valueOf(account.getAccountId())));

                if (locked.getStatus() != AccountStatus.ACTIVE) {
                    throw new ValidationException("Cannot withdraw from account with status: " + locked.getStatus());
                }

                // Re-validate withdrawal against locked balance using polymorphism
                if (!locked.canWithdraw(validatedAmount)) {
                    throw new InsufficientBalanceException(locked.getBalance(), validatedAmount);
                }

                BigDecimal newBalance = locked.getBalance().subtract(validatedAmount).setScale(2, RoundingMode.HALF_UP);
                accountDAO.updateBalance(conn, locked.getAccountId(), newBalance);
                locked.setBalance(newBalance);

                // Ledger transaction
                Transaction tx = new Transaction(
                        locked.getAccountId(),
                        null,
                        TransactionType.WITHDRAWAL,
                        validatedAmount,
                        "Cash withdrawal"
                );
                transactionDAO.create(conn, tx);

                conn.commit();
                return locked;
            } catch (SQLException | BankingException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private void verifyOwnership(long userId, Account account) throws UnauthorizedAccessException {
        if (!account.getUserId().equals(userId)) {
            throw new UnauthorizedAccessException(userId, account.getAccountNumber());
        }
    }

    private String generateUniqueAccountNumber(AccountType type) throws SQLException {
        String prefix = (type == AccountType.SAVINGS) ? "SA-" : "CA-";
        for (int attempts = 0; attempts < 100; attempts++) {
            int num = 100000 + random.nextInt(900000);
            String candidate = prefix + num;
            if (!accountDAO.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new SQLException("Unable to generate unique account number after multiple attempts");
    }
}
