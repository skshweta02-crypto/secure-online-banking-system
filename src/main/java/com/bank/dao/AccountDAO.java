package com.bank.dao;

import com.bank.config.DatabaseConnectionFactory;
import com.bank.model.Account;
import com.bank.model.AccountStatus;
import com.bank.model.AccountType;
import com.bank.model.CurrentAccount;
import com.bank.model.SavingsAccount;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Account entities.
 * Supports row-level pessimistic locking (SELECT ... FOR UPDATE) and atomic updates.
 */
public class AccountDAO {

    private static final String SQL_INSERT =
            "INSERT INTO accounts (account_number, user_id, account_type, balance, status) VALUES (?, ?, ?, ?, ?)";
    private static final String SQL_FIND_BY_ID =
            "SELECT account_id, account_number, user_id, account_type, balance, status, created_at FROM accounts WHERE account_id = ?";
    private static final String SQL_FIND_BY_NUMBER =
            "SELECT account_id, account_number, user_id, account_type, balance, status, created_at FROM accounts WHERE account_number = ?";
    private static final String SQL_FIND_BY_USER_ID =
            "SELECT account_id, account_number, user_id, account_type, balance, status, created_at FROM accounts WHERE user_id = ? ORDER BY account_id ASC";
    private static final String SQL_FIND_BY_ID_FOR_UPDATE =
            "SELECT account_id, account_number, user_id, account_type, balance, status, created_at FROM accounts WHERE account_id = ? FOR UPDATE";
    private static final String SQL_UPDATE_BALANCE =
            "UPDATE accounts SET balance = ? WHERE account_id = ?";
    private static final String SQL_UPDATE_STATUS =
            "UPDATE accounts SET status = ? WHERE account_id = ?";
    private static final String SQL_EXISTS_BY_NUMBER =
            "SELECT 1 FROM accounts WHERE account_number = ? LIMIT 1";

    public Account create(Account account) throws SQLException {
        try (Connection conn = DatabaseConnectionFactory.getConnection()) {
            return create(conn, account);
        }
    }

    public Account create(Connection conn, Account account) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, account.getAccountNumber());
            stmt.setLong(2, account.getUserId());
            stmt.setString(3, account.getAccountType().name());
            stmt.setBigDecimal(4, account.getBalance());
            stmt.setString(5, account.getStatus().name());

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    account.setAccountId(rs.getLong(1));
                } else {
                    throw new SQLException("Failed to retrieve generated account_id");
                }
            }
            return account;
        }
    }

    public Optional<Account> findById(long accountId) throws SQLException {
        try (Connection conn = DatabaseConnectionFactory.getConnection()) {
            return findById(conn, accountId);
        }
    }

    public Optional<Account> findById(Connection conn, long accountId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_ID)) {
            stmt.setLong(1, accountId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToAccount(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Account> findByAccountNumber(String accountNumber) throws SQLException {
        try (Connection conn = DatabaseConnectionFactory.getConnection()) {
            return findByAccountNumber(conn, accountNumber);
        }
    }

    public Optional<Account> findByAccountNumber(Connection conn, String accountNumber) throws SQLException {
        if (accountNumber == null) return Optional.empty();
        try (PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_NUMBER)) {
            stmt.setString(1, accountNumber.trim().toUpperCase());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToAccount(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<Account> findByUserId(long userId) throws SQLException {
        try (Connection conn = DatabaseConnectionFactory.getConnection()) {
            return findByUserId(conn, userId);
        }
    }

    public List<Account> findByUserId(Connection conn, long userId) throws SQLException {
        List<Account> accounts = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_USER_ID)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    accounts.add(mapResultSetToAccount(rs));
                }
            }
        }
        return accounts;
    }

    /**
     * Executes SELECT ... FOR UPDATE within an active transaction to lock the account row.
     */
    public Optional<Account> findByIdForUpdate(Connection conn, long accountId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_ID_FOR_UPDATE)) {
            stmt.setLong(1, accountId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToAccount(rs));
                }
            }
        }
        return Optional.empty();
    }

    public void updateBalance(Connection conn, long accountId, BigDecimal newBalance) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_BALANCE)) {
            stmt.setBigDecimal(1, newBalance);
            stmt.setLong(2, accountId);
            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Account update failed: row not found for account_id " + accountId);
            }
        }
    }

    public void updateStatus(long accountId, AccountStatus status) throws SQLException {
        try (Connection conn = DatabaseConnectionFactory.getConnection()) {
            updateStatus(conn, accountId, status);
        }
    }

    public void updateStatus(Connection conn, long accountId, AccountStatus status) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_STATUS)) {
            stmt.setString(1, status.name());
            stmt.setLong(2, accountId);
            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Status update failed: row not found for account_id " + accountId);
            }
        }
    }

    public boolean existsByAccountNumber(String accountNumber) throws SQLException {
        try (Connection conn = DatabaseConnectionFactory.getConnection()) {
            return existsByAccountNumber(conn, accountNumber);
        }
    }

    public boolean existsByAccountNumber(Connection conn, String accountNumber) throws SQLException {
        if (accountNumber == null) return false;
        try (PreparedStatement stmt = conn.prepareStatement(SQL_EXISTS_BY_NUMBER)) {
            stmt.setString(1, accountNumber.trim().toUpperCase());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private Account mapResultSetToAccount(ResultSet rs) throws SQLException {
        String typeStr = rs.getString("account_type");
        AccountType type = AccountType.fromString(typeStr);

        Account account;
        if (type == AccountType.SAVINGS) {
            account = new SavingsAccount();
        } else {
            account = new CurrentAccount();
        }

        account.setAccountId(rs.getLong("account_id"));
        account.setAccountNumber(rs.getString("account_number"));
        account.setUserId(rs.getLong("user_id"));
        account.setAccountType(type);
        account.setBalance(rs.getBigDecimal("balance"));
        account.setStatus(AccountStatus.fromString(rs.getString("status")));

        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            account.setCreatedAt(ts.toLocalDateTime());
        }

        return account;
    }
}
