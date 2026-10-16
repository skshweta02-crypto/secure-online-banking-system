package com.bank.dao;

import com.bank.config.DatabaseConnectionFactory;
import com.bank.model.Transaction;
import com.bank.model.TransactionType;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for financial Transaction ledger entries.
 * Enforces atomic insertion within caller transactions and flexible statement filtering.
 */
public class TransactionDAO {

    private static final String SQL_INSERT =
            "INSERT INTO transactions (account_id, transfer_id, transaction_type, amount, description) VALUES (?, ?, ?, ?, ?)";
    private static final String SQL_FIND_BY_ID =
            "SELECT transaction_id, account_id, transfer_id, transaction_type, amount, description, created_at FROM transactions WHERE transaction_id = ?";
    private static final String SQL_FIND_BY_ACCOUNT_ID =
            "SELECT transaction_id, account_id, transfer_id, transaction_type, amount, description, created_at FROM transactions WHERE account_id = ? ORDER BY created_at DESC, transaction_id DESC";
    private static final String SQL_FIND_BY_TRANSFER_ID =
            "SELECT transaction_id, account_id, transfer_id, transaction_type, amount, description, created_at FROM transactions WHERE transfer_id = ? ORDER BY transaction_id ASC";

    public Transaction create(Transaction tx) throws SQLException {
        try (Connection conn = DatabaseConnectionFactory.getConnection()) {
            return create(conn, tx);
        }
    }

    public Transaction create(Connection conn, Transaction tx) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, tx.getAccountId());
            stmt.setString(2, tx.getTransferId());
            stmt.setString(3, tx.getTransactionType().name());
            stmt.setBigDecimal(4, tx.getAmount());
            stmt.setString(5, tx.getDescription());

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    tx.setTransactionId(rs.getLong(1));
                } else {
                    throw new SQLException("Failed to retrieve generated transaction_id");
                }
            }
            return tx;
        }
    }

    public Optional<Transaction> findById(long transactionId) throws SQLException {
        try (Connection conn = DatabaseConnectionFactory.getConnection()) {
            return findById(conn, transactionId);
        }
    }

    public Optional<Transaction> findById(Connection conn, long transactionId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_ID)) {
            stmt.setLong(1, transactionId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToTransaction(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<Transaction> findByAccountId(long accountId) throws SQLException {
        try (Connection conn = DatabaseConnectionFactory.getConnection()) {
            return findByAccountId(conn, accountId);
        }
    }

    public List<Transaction> findByAccountId(Connection conn, long accountId) throws SQLException {
        List<Transaction> list = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_ACCOUNT_ID)) {
            stmt.setLong(1, accountId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToTransaction(rs));
                }
            }
        }
        return list;
    }

    public List<Transaction> findByTransferId(String transferId) throws SQLException {
        try (Connection conn = DatabaseConnectionFactory.getConnection()) {
            return findByTransferId(conn, transferId);
        }
    }

    public List<Transaction> findByTransferId(Connection conn, String transferId) throws SQLException {
        List<Transaction> list = new ArrayList<>();
        if (transferId == null) return list;
        try (PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_TRANSFER_ID)) {
            stmt.setString(1, transferId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToTransaction(rs));
                }
            }
        }
        return list;
    }

    /**
     * Retrieves transactions for an account with optional type and date range filters.
     */
    public List<Transaction> findFiltered(long accountId, TransactionType type, LocalDate startDate, LocalDate endDate) throws SQLException {
        try (Connection conn = DatabaseConnectionFactory.getConnection()) {
            return findFiltered(conn, accountId, type, startDate, endDate);
        }
    }

    public List<Transaction> findFiltered(Connection conn, long accountId, TransactionType type, LocalDate startDate, LocalDate endDate) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT transaction_id, account_id, transfer_id, transaction_type, amount, description, created_at FROM transactions WHERE account_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(accountId);

        if (type != null) {
            sql.append(" AND transaction_type = ?");
            params.add(type.name());
        }
        if (startDate != null) {
            sql.append(" AND created_at >= ?");
            params.add(Timestamp.valueOf(startDate.atStartOfDay()));
        }
        if (endDate != null) {
            sql.append(" AND created_at <= ?");
            params.add(Timestamp.valueOf(endDate.atTime(23, 59, 59)));
        }

        sql.append(" ORDER BY created_at DESC, transaction_id DESC");

        List<Transaction> list = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToTransaction(rs));
                }
            }
        }
        return list;
    }

    private Transaction mapResultSetToTransaction(ResultSet rs) throws SQLException {
        Transaction tx = new Transaction();
        tx.setTransactionId(rs.getLong("transaction_id"));
        tx.setAccountId(rs.getLong("account_id"));
        tx.setTransferId(rs.getString("transfer_id"));
        tx.setTransactionType(TransactionType.fromString(rs.getString("transaction_type")));
        tx.setAmount(rs.getBigDecimal("amount"));
        tx.setDescription(rs.getString("description"));

        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            tx.setCreatedAt(ts.toLocalDateTime());
        }
        return tx;
    }
}
