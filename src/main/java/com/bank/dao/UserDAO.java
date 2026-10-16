package com.bank.dao;

import com.bank.config.DatabaseConnectionFactory;
import com.bank.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Optional;

/**
 * Data Access Object for User entities.
 * Supports standalone and transactional connections using PreparedStatement.
 */
public class UserDAO {

    private static final String SQL_INSERT =
            "INSERT INTO users (full_name, email, password_hash) VALUES (?, ?, ?)";
    private static final String SQL_FIND_BY_ID =
            "SELECT user_id, full_name, email, password_hash, created_at FROM users WHERE user_id = ?";
    private static final String SQL_FIND_BY_EMAIL =
            "SELECT user_id, full_name, email, password_hash, created_at FROM users WHERE email = ?";
    private static final String SQL_EXISTS_BY_EMAIL =
            "SELECT 1 FROM users WHERE email = ? LIMIT 1";

    public User create(User user) throws SQLException {
        try (Connection conn = DatabaseConnectionFactory.getConnection()) {
            return create(conn, user);
        }
    }

    public User create(Connection conn, User user) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, user.getFullName());
            stmt.setString(2, user.getEmail());
            stmt.setString(3, user.getPasswordHash());

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    user.setUserId(rs.getLong(1));
                } else {
                    throw new SQLException("Failed to retrieve generated user_id");
                }
            }
            return user;
        }
    }

    public Optional<User> findById(long userId) throws SQLException {
        try (Connection conn = DatabaseConnectionFactory.getConnection()) {
            return findById(conn, userId);
        }
    }

    public Optional<User> findById(Connection conn, long userId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_ID)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToUser(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<User> findByEmail(String email) throws SQLException {
        try (Connection conn = DatabaseConnectionFactory.getConnection()) {
            return findByEmail(conn, email);
        }
    }

    public Optional<User> findByEmail(Connection conn, String email) throws SQLException {
        if (email == null) return Optional.empty();
        try (PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_EMAIL)) {
            stmt.setString(1, email.trim().toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToUser(rs));
                }
            }
        }
        return Optional.empty();
    }

    public boolean existsByEmail(String email) throws SQLException {
        try (Connection conn = DatabaseConnectionFactory.getConnection()) {
            return existsByEmail(conn, email);
        }
    }

    public boolean existsByEmail(Connection conn, String email) throws SQLException {
        if (email == null) return false;
        try (PreparedStatement stmt = conn.prepareStatement(SQL_EXISTS_BY_EMAIL)) {
            stmt.setString(1, email.trim().toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setUserId(rs.getLong("user_id"));
        user.setFullName(rs.getString("full_name"));
        user.setEmail(rs.getString("email"));
        user.setPasswordHash(rs.getString("password_hash"));

        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            user.setCreatedAt(ts.toLocalDateTime());
        }
        return user;
    }
}
