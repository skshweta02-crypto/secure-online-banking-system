package com.bank.ui;

import com.bank.config.DatabaseConnectionFactory;
import com.bank.dao.AccountDAO;
import com.bank.dao.TransactionDAO;
import com.bank.dao.UserDAO;
import com.bank.service.AccountService;
import com.bank.service.AuthService;
import com.bank.service.TransactionService;
import com.bank.service.TransferService;
import com.bank.service.UserSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleUITest {

    private static final String H2_URL = "jdbc:h2:mem:secure_banking_ui_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    @BeforeAll
    static void setup() {
        DatabaseConnectionFactory.setCredentials(H2_URL, "sa", "");
    }

    @AfterAll
    static void tearDown() {
        DatabaseConnectionFactory.loadConfiguration();
    }

    @Test
    @DisplayName("ConsoleUI handles exit option gracefully")
    void testExitGracefully() {
        // User inputs option 3 (Exit)
        String simulatedInput = "3\n";
        ByteArrayInputStream in = new ByteArrayInputStream(simulatedInput.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes);

        UserDAO userDAO = new UserDAO();
        AccountDAO accountDAO = new AccountDAO();
        TransactionDAO transactionDAO = new TransactionDAO();

        ConsoleUI ui = new ConsoleUI(
                new AuthService(userDAO),
                new AccountService(accountDAO, transactionDAO),
                new TransferService(accountDAO, transactionDAO),
                new TransactionService(transactionDAO, accountDAO),
                new UserSession(),
                in,
                out
        );

        ui.start();

        String output = outBytes.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("SECURE ONLINE BANKING & WALLET SYSTEM"));
        assertTrue(output.contains("Goodbye"));
    }

    @Test
    @DisplayName("ConsoleUI handles invalid menu choice gracefully")
    void testInvalidMenuChoice() {
        // Invalid choice '9', then '3' to exit
        String simulatedInput = "9\n3\n";
        ByteArrayInputStream in = new ByteArrayInputStream(simulatedInput.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes);

        UserDAO userDAO = new UserDAO();
        AccountDAO accountDAO = new AccountDAO();
        TransactionDAO transactionDAO = new TransactionDAO();

        ConsoleUI ui = new ConsoleUI(
                new AuthService(userDAO),
                new AccountService(accountDAO, transactionDAO),
                new TransferService(accountDAO, transactionDAO),
                new TransactionService(transactionDAO, accountDAO),
                new UserSession(),
                in,
                out
        );

        ui.start();

        String output = outBytes.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Invalid choice"));
    }
}
