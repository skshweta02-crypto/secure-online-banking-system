package com.bank;

import com.bank.config.DatabaseConnectionFactory;
import com.bank.ui.ConsoleUI;

/**
 * Main application entry point for the Secure Online Banking & Wallet System.
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("[INFO] Booting Secure Online Banking System...");
        System.out.println("[INFO] Database Target: " + DatabaseConnectionFactory.getDbUrl());

        if (DatabaseConnectionFactory.testConnection()) {
            System.out.println("[INFO] MySQL connection established successfully.");
        } else {
            System.out.println("[WARN] Live MySQL instance is currently unreachable at default address.");
            System.out.println("[WARN] Ensure MySQL 8+ is running and 'secure_banking' database is created using schema.sql.");
            System.out.println("[WARN] You can configure DB_URL, DB_USER, DB_PASSWORD via environment variables or db.properties.");
        }

        ConsoleUI ui = new ConsoleUI();
        ui.start();
    }
}
