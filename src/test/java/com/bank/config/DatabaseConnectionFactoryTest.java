package com.bank.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseConnectionFactoryTest {

    @Test
    @DisplayName("Configuration loads default or configured DB parameters")
    void testConfigurationLoading() {
        assertDoesNotThrow(DatabaseConnectionFactory::loadConfiguration);
        assertNotNull(DatabaseConnectionFactory.getDbUrl());
        assertNotNull(DatabaseConnectionFactory.getDbUser());
        assertTrue(DatabaseConnectionFactory.getDbUrl().startsWith("jdbc:mysql://"));
    }

    @Test
    @DisplayName("schema.sql resource is present and readable on classpath")
    void testSchemaResourcePresent() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("db/schema.sql");
        assertNotNull(is, "db/schema.sql must exist on classpath");
    }

    @Test
    @DisplayName("Connection attempt to invalid host throws SQLException without hanging")
    void testInvalidConnectionThrows() {
        DatabaseConnectionFactory.setCredentials("jdbc:mysql://invalid-nonexistent-host:3306/db?connectTimeout=1000", "user", "pass");
        assertThrows(SQLException.class, DatabaseConnectionFactory::getConnection);
        // Reset configuration
        DatabaseConnectionFactory.loadConfiguration();
    }
}
