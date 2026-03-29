package com.example;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class BankAccountTest {

    private BankAccount account1;
    private BankAccount account2;

    @BeforeEach
    void setUp() {
        account1 = new BankAccount("123456789", 100);
        account2 = new BankAccount("987654321", 50);
    }

    @Test
    void testGetters() {
        assertEquals("123456789", account1.getAccountNumber());
        assertEquals(100, account1.getBalance(), 0.01);
        assertEquals("987654321", account2.getAccountNumber());
        assertEquals(50, account2.getBalance(), 0.01);
    }

    @Test
    void testDeposit() {
        account1.deposit(50);
        assertEquals(150, account1.getBalance(), 0.01);
    }

    @Test
    void testWithdraw() {
        account2.withdraw(30);
        assertEquals(20, account2.getBalance(), 0.01);
    }

    @Test
    void testTransferTo() {
        account1.transferTo(account2, 20);
        assertEquals(80, account1.getBalance(), 0.01);
        assertEquals(70, account2.getBalance(), 0.01);
    }
}