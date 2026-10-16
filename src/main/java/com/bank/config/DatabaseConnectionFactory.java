package com.bank.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Factory providing properly scoped JDBC connections.
 * Reads connection settings from environment variables, local db.properties,
 * or defaults. Never caches a mutable connection across independent threads.
 */
public final class DatabaseConnectionFactory {

    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/secure_banking?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "";

    private static String dbUrl;
    private static String dbUser;
    private static String dbPassword;

    static {
        loadConfiguration();
    }

    private DatabaseConnectionFactory() {
        // Utility class
    }

    public static synchronized void loadConfiguration() {
        // 1. Try Environment variables
        String envUrl = System.getenv("DB_URL");
        String envUser = System.getenv("DB_USER");
        String envPass = System.getenv("DB_PASSWORD");

        if (envUrl != null && !envUrl.trim().isEmpty()) {
            dbUrl = envUrl.trim();
            dbUser = envUser != null ? envUser.trim() : DEFAULT_USER;
            dbPassword = envPass != null ? envPass : "";
            return;
        }

        // 2. Try external db.properties or classpath db.properties
        Properties props = new Properties();
        File externalProps = new File("db.properties");
        boolean loaded = false;

        if (externalProps.exists() && externalProps.isFile()) {
            try (InputStream in = new FileInputStream(externalProps)) {
                props.load(in);
                loaded = true;
            } catch (IOException ignored) {
                // Fall back to classpath
            }
        }

        if (!loaded) {
            try (InputStream in = DatabaseConnectionFactory.class.getClassLoader().getResourceAsStream("db.properties")) {
                if (in != null) {
                    props.load(in);
                    loaded = true;
                }
            } catch (IOException ignored) {
                // Fall back to defaults
            }
        }

        if (loaded) {
            dbUrl = props.getProperty("db.url", DEFAULT_URL).trim();
            dbUser = props.getProperty("db.user", DEFAULT_USER).trim();
            dbPassword = props.getProperty("db.password", DEFAULT_PASSWORD);
        } else {
            dbUrl = DEFAULT_URL;
            dbUser = DEFAULT_USER;
            dbPassword = DEFAULT_PASSWORD;
        }
    }

    /**
     * Creates and returns a new distinct JDBC connection.
     * Callers are responsible for closing it or using try-with-resources.
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    /**
     * Tests if the database connection can be established.
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed() && conn.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Executes SQL DDL statements from an input stream (e.g., schema.sql).
     */
    public static void executeSqlScript(Connection conn, InputStream inputStream) throws IOException, SQLException {
        if (inputStream == null) {
            throw new IllegalArgumentException("Script InputStream cannot be null");
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
             Statement stmt = conn.createStatement()) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                // Skip empty lines and comments
                if (trimmed.isEmpty() || trimmed.startsWith("--") || trimmed.startsWith("#") || trimmed.startsWith("/*")) {
                    continue;
                }
                sb.append(line).append("\n");
                if (trimmed.endsWith(";")) {
                    String sql = sb.toString().trim();
                    if (sql.endsWith(";")) {
                        sql = sql.substring(0, sql.length() - 1);
                    }
                    if (!sql.isEmpty()) {
                        stmt.execute(sql);
                    }
                    sb.setLength(0);
                }
            }
        }
    }

    public static String getDbUrl() {
        return dbUrl;
    }

    public static String getDbUser() {
        return dbUser;
    }

    /**
     * Package-private or test setter to allow running in-memory tests or custom URLs.
     */
    public static synchronized void setCredentials(String url, String user, String password) {
        dbUrl = url;
        dbUser = user;
        dbPassword = password;
    }
}
