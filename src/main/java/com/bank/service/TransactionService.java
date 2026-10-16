package com.bank.service;

import com.bank.dao.AccountDAO;
import com.bank.dao.TransactionDAO;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.BankingException;
import com.bank.exception.UnauthorizedAccessException;
import com.bank.model.Account;
import com.bank.model.Transaction;
import com.bank.model.TransactionType;
import com.bank.util.InputValidator;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Service for querying account statements and applying audit ledger filters.
 */
public class TransactionService {

    private final TransactionDAO transactionDAO;
    private final AccountDAO accountDAO;

    public TransactionService() {
        this(new TransactionDAO(), new AccountDAO());
    }

    public TransactionService(TransactionDAO transactionDAO, AccountDAO accountDAO) {
        this.transactionDAO = transactionDAO;
        this.accountDAO = accountDAO;
    }

    /**
     * Retrieves the complete transaction history for an account after verifying user ownership.
     */
    public List<Transaction> getAccountStatement(long userId, String accountNumber)
            throws BankingException, SQLException {

        Account account = verifyOwnershipAndGetAccount(userId, accountNumber);
        return transactionDAO.findByAccountId(account.getAccountId());
    }

    /**
     * Retrieves filtered transactions for an account by type and date range after verifying ownership.
     */
    public List<Transaction> getFilteredStatement(long userId, String accountNumber,
                                                  TransactionType type, LocalDate fromDate, LocalDate toDate)
            throws BankingException, SQLException {

        Account account = verifyOwnershipAndGetAccount(userId, accountNumber);
        return transactionDAO.findFiltered(account.getAccountId(), type, fromDate, toDate);
    }

    private Account verifyOwnershipAndGetAccount(long userId, String accountNumber)
            throws BankingException, SQLException {

        InputValidator.validateAccountNumber(accountNumber);
        String normalized = accountNumber.trim().toUpperCase();

        Account account = accountDAO.findByAccountNumber(normalized)
                .orElseThrow(() -> new AccountNotFoundException("account number", normalized));

        if (!account.getUserId().equals(userId)) {
            throw new UnauthorizedAccessException(userId, normalized);
        }

        return account;
    }
}
