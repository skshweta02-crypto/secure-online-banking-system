package com.bank.model;

import com.bank.exception.BankingException;
import com.bank.exception.InsufficientBalanceException;
import com.bank.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelTest {

    @Test
    @DisplayName("SavingsAccount enforces minimum balance of $500.00 on withdrawal")
    void testSavingsAccountRules() throws BankingException {
        SavingsAccount savings = new SavingsAccount("SA-1001", 1L, new BigDecimal("1000.00"));
        assertEquals(AccountType.SAVINGS, savings.getAccountType());
        assertEquals(new BigDecimal("500.00"), savings.getMinimumBalance());

        // Permissible withdrawal leaving >= 500.00
        savings.withdraw(new BigDecimal("400.00"));
        assertEquals(new BigDecimal("600.00"), savings.getBalance());

        // Impermissible withdrawal violating minimum balance
        InsufficientBalanceException ex = assertThrows(InsufficientBalanceException.class, () ->
                savings.withdraw(new BigDecimal("200.00")));
        assertEquals(new BigDecimal("600.00"), ex.getCurrentBalance());
        assertEquals(new BigDecimal("200.00"), ex.getRequestedAmount());
    }

    @Test
    @DisplayName("CurrentAccount allows withdrawal down to $0.00")
    void testCurrentAccountRules() throws BankingException {
        CurrentAccount current = new CurrentAccount("CA-2001", 2L, new BigDecimal("200.00"));
        assertEquals(AccountType.CURRENT, current.getAccountType());
        assertEquals(BigDecimal.ZERO.setScale(2), current.getMinimumBalance());

        // Full balance withdrawal down to 0.00
        current.withdraw(new BigDecimal("200.00"));
        assertEquals(BigDecimal.ZERO.setScale(2), current.getBalance());

        // Overdraft attempts without sufficient balance throw InsufficientBalanceException
        assertThrows(InsufficientBalanceException.class, () ->
                current.withdraw(new BigDecimal("10.00")));
    }

    @Test
    @DisplayName("Deposit and withdrawal reject negative or zero amounts")
    void testNegativeOrZeroAmountValidation() {
        SavingsAccount account = new SavingsAccount("SA-1002", 1L, new BigDecimal("600.00"));

        assertThrows(ValidationException.class, () -> account.deposit(new BigDecimal("-50.00")));
        assertThrows(ValidationException.class, () -> account.deposit(BigDecimal.ZERO));
        assertThrows(ValidationException.class, () -> account.withdraw(new BigDecimal("-10.00")));
        assertThrows(ValidationException.class, () -> account.withdraw(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Inactive or suspended accounts reject deposits and withdrawals")
    void testInactiveAccountRejection() {
        SavingsAccount account = new SavingsAccount("SA-1003", 1L, new BigDecimal("1000.00"));
        account.setStatus(AccountStatus.SUSPENDED);

        assertThrows(ValidationException.class, () -> account.deposit(new BigDecimal("100.00")));
        assertThrows(ValidationException.class, () -> account.withdraw(new BigDecimal("50.00")));
    }

    @Test
    @DisplayName("User toString must never expose password hash")
    void testUserToStringDoesNotLeakPasswordHash() {
        User user = new User(1L, "Alice Smith", "alice@example.com", "$2a$12$SecretHashValue", null);
        String str = user.toString();
        assertTrue(str.contains("Alice Smith"));
        assertTrue(str.contains("alice@example.com"));
        assertFalse(str.contains("SecretHashValue"));
    }

    @Test
    @DisplayName("Transaction correctly identifies debit vs credit types")
    void testTransactionDirection() {
        Transaction debitTx = new Transaction(1L, "tx-1", TransactionType.TRANSFER_DEBIT, new BigDecimal("50.00"), "Transfer out");
        Transaction creditTx = new Transaction(2L, "tx-1", TransactionType.TRANSFER_CREDIT, new BigDecimal("50.00"), "Transfer in");

        assertTrue(debitTx.getTransactionType().isDebit());
        assertFalse(debitTx.getTransactionType().isCredit());
        assertTrue(creditTx.getTransactionType().isCredit());
        assertFalse(creditTx.getTransactionType().isDebit());
    }
}
