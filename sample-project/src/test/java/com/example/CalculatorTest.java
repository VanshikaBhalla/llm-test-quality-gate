package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CalculatorTest {
    private Calculator calculator;

    @BeforeEach
    void setup() {
        calculator = new Calculator();
    }

    @Test
    void testAdd() {
        assertEquals(5, calculator.add(2, 3));
        assertEquals(-1, calculator.add(-2, 1));
        assertEquals(0, calculator.add(0, 0));
    }

    @Test
    void testDivide() {
        assertEquals(2, calculator.divide(4, 2));
        assertThrows(ArithmeticException.class, () -> calculator.divide(2, 0));
        assertThrows(IllegalArgumentException.class, () -> calculator.divide(-2, -0)); // negative divisor with negative zero
    }
}