package com.bank.ui;

import com.bank.exception.BankingException;
import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.model.Transaction;
import com.bank.model.TransactionType;
import com.bank.model.TransferReceipt;
import com.bank.model.User;
import com.bank.service.AccountService;
import com.bank.service.AuthService;
import com.bank.service.TransactionService;
import com.bank.service.TransferService;
import com.bank.service.UserSession;
import com.bank.util.InputValidator;

import java.io.InputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;

/**
 * Console-based user interface for the Secure Online Banking & Wallet System.
 */
public class ConsoleUI {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AuthService authService;
    private final AccountService accountService;
    private final TransferService transferService;
    private final TransactionService transactionService;
    private final UserSession session;
    private final Scanner scanner;
    private final PrintStream out;

    public ConsoleUI() {
        this(new AuthService(), new AccountService(), new TransferService(), new TransactionService(),
                new UserSession(), System.in, System.out);
    }

    public ConsoleUI(AuthService authService, AccountService accountService, TransferService transferService,
                     TransactionService transactionService, UserSession session, InputStream in, PrintStream out) {
        this.authService = authService;
        this.accountService = accountService;
        this.transferService = transferService;
        this.transactionService = transactionService;
        this.session = session;
        this.scanner = new Scanner(in);
        this.out = out;
    }

    public void start() {
        printBanner();
        boolean running = true;
        while (running) {
            try {
                if (!session.isLoggedIn()) {
                    running = handleUnauthenticatedMenu();
                } else {
                    handleAuthenticatedMenu();
                }
            } catch (Exception e) {
                out.println("[ERROR] An unexpected system error occurred: " + e.getMessage());
            }
        }
        out.println("\nThank you for using Secure Online Banking System. Goodbye!");
    }

    private boolean handleUnauthenticatedMenu() {
        out.println("\n========================================");
        out.println("          MAIN MENU (GUEST)            ");
        out.println("========================================");
        out.println("1. Register");
        out.println("2. Login");
        out.println("3. Exit");
        out.print("Select an option (1-3): ");

        String choice = scanner.nextLine().trim();
        switch (choice) {
            case "1" -> handleRegister();
            case "2" -> handleLogin();
            case "3" -> { return false; }
            default -> out.println("[!] Invalid choice. Please enter 1, 2, or 3.");
        }
        return true;
    }

    private void handleAuthenticatedMenu() {
        User user = session.getCurrentUser();
        out.println("\n========================================");
        out.printf("   WELCOME, %s!%n", user.getFullName().toUpperCase());
        out.println("========================================");
        out.println("1. Open Savings Account");
        out.println("2. Open Current Account");
        out.println("3. View Accounts");
        out.println("4. Deposit");
        out.println("5. Withdraw");
        out.println("6. Transfer Funds");
        out.println("7. View Statement");
        out.println("8. Logout");
        out.print("Select an option (1-8): ");

        String choice = scanner.nextLine().trim();
        switch (choice) {
            case "1" -> handleOpenAccount(AccountType.SAVINGS);
            case "2" -> handleOpenAccount(AccountType.CURRENT);
            case "3" -> handleViewAccounts();
            case "4" -> handleDeposit();
            case "5" -> handleWithdraw();
            case "6" -> handleTransferFunds();
            case "7" -> handleViewStatement();
            case "8" -> handleLogout();
            default -> out.println("[!] Invalid choice. Please enter a number between 1 and 8.");
        }
    }

    private void handleRegister() {
        out.println("\n--- USER REGISTRATION ---");
        try {
            out.print("Enter full name: ");
            String fullName = scanner.nextLine();

            out.print("Enter email address: ");
            String email = scanner.nextLine();

            out.print("Enter password (min 8 chars, letters & digits): ");
            String password = scanner.nextLine();

            User registered = authService.register(fullName, email, password);
            out.println("[SUCCESS] Registration successful! You can now log in with email: " + registered.getEmail());
        } catch (BankingException | SQLException e) {
            out.println("[FAILED] Registration error: " + e.getMessage());
        }
    }

    private void handleLogin() {
        out.println("\n--- USER LOGIN ---");
        try {
            out.print("Enter email: ");
            String email = scanner.nextLine();

            out.print("Enter password: ");
            String password = scanner.nextLine();

            User user = authService.authenticate(email, password);
            session.login(user);
            out.println("[SUCCESS] Login successful! Welcome back, " + user.getFullName() + ".");
        } catch (BankingException | SQLException e) {
            out.println("[FAILED] Login error: " + e.getMessage());
        }
    }

    private void handleOpenAccount(AccountType type) {
        out.printf("%n--- OPEN %s ---%n", type.getDisplayName().toUpperCase());
        try {
            if (type == AccountType.SAVINGS) {
                out.println("Note: Savings accounts require a minimum initial deposit of $500.00.");
            }
            out.print("Enter initial deposit amount ($): ");
            String amountStr = scanner.nextLine();
            BigDecimal amount = InputValidator.parseAndValidateAmount(amountStr);

            Account account = accountService.openAccount(session.getCurrentUserId(), type, amount);
            out.println("[SUCCESS] Account successfully opened!");
            out.println("  Account Number : " + account.getAccountNumber());
            out.println("  Account Type   : " + account.getAccountType().getDisplayName());
            out.printf("  Initial Balance: $%.2f%n", account.getBalance());
        } catch (BankingException | SQLException e) {
            out.println("[FAILED] Could not open account: " + e.getMessage());
        }
    }

    private void handleViewAccounts() {
        out.println("\n--- MY ACCOUNTS ---");
        try {
            List<Account> accounts = accountService.getUserAccounts(session.getCurrentUserId());
            if (accounts.isEmpty()) {
                out.println("You do not have any active accounts. Open a Savings or Current account first.");
                return;
            }

            out.println("----------------------------------------------------------------------------------");
            out.printf("%-15s %-18s %-15s %-12s %-20s%n", "Account No", "Type", "Balance ($)", "Status", "Created At");
            out.println("----------------------------------------------------------------------------------");
            for (Account acc : accounts) {
                out.printf("%-15s %-18s %-15.2f %-12s %-20s%n",
                        acc.getAccountNumber(),
                        acc.getAccountType().getDisplayName(),
                        acc.getBalance(),
                        acc.getStatus(),
                        acc.getCreatedAt() != null ? acc.getCreatedAt().format(DATE_FORMATTER) : "N/A");
            }
            out.println("----------------------------------------------------------------------------------");
        } catch (SQLException e) {
            out.println("[FAILED] Error loading accounts: " + e.getMessage());
        }
    }

    private void handleDeposit() {
        out.println("\n--- DEPOSIT FUNDS ---");
        try {
            out.print("Enter account number: ");
            String accountNumber = scanner.nextLine();

            out.print("Enter deposit amount ($): ");
            String amountStr = scanner.nextLine();
            BigDecimal amount = InputValidator.parseAndValidateAmount(amountStr);

            Account updated = accountService.deposit(session.getCurrentUserId(), accountNumber, amount);
            out.println("[SUCCESS] Deposit completed successfully!");
            out.printf("  Account Number : %s%n", updated.getAccountNumber());
            out.printf("  Deposited      : $%.2f%n", amount);
            out.printf("  New Balance    : $%.2f%n", updated.getBalance());
        } catch (BankingException | SQLException e) {
            out.println("[FAILED] Deposit failed: " + e.getMessage());
        }
    }

    private void handleWithdraw() {
        out.println("\n--- WITHDRAW FUNDS ---");
        try {
            out.print("Enter account number: ");
            String accountNumber = scanner.nextLine();

            out.print("Enter withdrawal amount ($): ");
            String amountStr = scanner.nextLine();
            BigDecimal amount = InputValidator.parseAndValidateAmount(amountStr);

            Account updated = accountService.withdraw(session.getCurrentUserId(), accountNumber, amount);
            out.println("[SUCCESS] Withdrawal completed successfully!");
            out.printf("  Account Number : %s%n", updated.getAccountNumber());
            out.printf("  Withdrawn      : $%.2f%n", amount);
            out.printf("  New Balance    : $%.2f%n", updated.getBalance());
        } catch (BankingException | SQLException e) {
            out.println("[FAILED] Withdrawal failed: " + e.getMessage());
        }
    }

    private void handleTransferFunds() {
        out.println("\n--- SECURE FUND TRANSFER ---");
        try {
            out.print("Enter your source account number: ");
            String fromAccount = scanner.nextLine();

            out.print("Enter destination account number: ");
            String toAccount = scanner.nextLine();

            out.print("Enter transfer amount ($): ");
            String amountStr = scanner.nextLine();
            BigDecimal amount = InputValidator.parseAndValidateAmount(amountStr);

            TransferReceipt receipt = transferService.transferFunds(
                    session.getCurrentUserId(), fromAccount, toAccount, amount);

            out.println("\n=======================================================");
            out.println("             TRANSFER CONFIRMATION RECEIPT            ");
            out.println("=======================================================");
            out.println("  Status            : COMPLETED (COMMITTED)");
            out.println("  Transfer ID       : " + receipt.getTransferId());
            out.println("  Timestamp         : " + receipt.getTimestamp().format(TIMESTAMP_FORMATTER));
            out.println("  From Account      : " + receipt.getFromAccountNumber());
            out.println("  To Account        : " + receipt.getToAccountNumber());
            out.printf("  Amount Transferred: $%.2f%n", receipt.getAmount());
            out.printf("  Remaining Balance : $%.2f%n", receipt.getSenderRemainingBalance());
            out.println("=======================================================");
        } catch (BankingException | SQLException e) {
            out.println("[FAILED] Fund transfer failed: " + e.getMessage());
        }
    }

    private void handleViewStatement() {
        out.println("\n--- ACCOUNT STATEMENT ---");
        try {
            out.print("Enter account number: ");
            String accountNumber = scanner.nextLine();

            out.println("Filter Options:");
            out.println("  1. All Transactions");
            out.println("  2. Filter by Transaction Type");
            out.println("  3. Filter by Date Range");
            out.print("Select an option (1-3): ");
            String filterChoice = scanner.nextLine().trim();

            List<Transaction> transactions;
            if ("2".equals(filterChoice)) {
                out.print("Enter transaction type (DEPOSIT, WITHDRAWAL, TRANSFER_DEBIT, TRANSFER_CREDIT): ");
                String typeStr = scanner.nextLine();
                TransactionType type = TransactionType.fromString(typeStr);
                transactions = transactionService.getFilteredStatement(
                        session.getCurrentUserId(), accountNumber, type, null, null);
            } else if ("3".equals(filterChoice)) {
                out.print("Enter start date (YYYY-MM-DD): ");
                LocalDate startDate = parseDate(scanner.nextLine());
                out.print("Enter end date (YYYY-MM-DD): ");
                LocalDate endDate = parseDate(scanner.nextLine());
                transactions = transactionService.getFilteredStatement(
                        session.getCurrentUserId(), accountNumber, null, startDate, endDate);
            } else {
                transactions = transactionService.getAccountStatement(session.getCurrentUserId(), accountNumber);
            }

            if (transactions.isEmpty()) {
                out.println("No transactions found matching your criteria.");
                return;
            }

            out.println("------------------------------------------------------------------------------------------------------------------------");
            out.printf("%-8s %-20s %-16s %-10s %-12s %-28s %-24s%n",
                    "Tx ID", "Timestamp", "Type", "Flow", "Amount ($)", "Description", "Transfer ID");
            out.println("------------------------------------------------------------------------------------------------------------------------");
            for (Transaction tx : transactions) {
                String flow = tx.getTransactionType().isDebit() ? "DEBIT (-)" : "CREDIT (+)";
                out.printf("%-8d %-20s %-16s %-10s %-12.2f %-28s %-24s%n",
                        tx.getTransactionId(),
                        tx.getCreatedAt().format(TIMESTAMP_FORMATTER),
                        tx.getTransactionType().name(),
                        flow,
                        tx.getAmount(),
                        tx.getDescription() != null ? tx.getDescription() : "",
                        tx.getTransferId() != null ? tx.getTransferId() : "-");
            }
            out.println("------------------------------------------------------------------------------------------------------------------------");
        } catch (BankingException | SQLException e) {
            out.println("[FAILED] Unable to generate statement: " + e.getMessage());
        }
    }

    private void handleLogout() {
        session.logout();
        out.println("[SUCCESS] You have been safely logged out.");
    }

    private LocalDate parseDate(String dateStr) throws BankingException {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new com.bank.exception.ValidationException("Invalid date format. Please use YYYY-MM-DD.");
        }
    }

    private void printBanner() {
        out.println("============================================================");
        out.println("        SECURE ONLINE BANKING & WALLET SYSTEM v1.0         ");
        out.println("           Core Java | JDBC | BCrypt | MySQL 8              ");
        out.println("============================================================");
    }
}
