package com.fintech.reconciliation.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FeeCalculatorTest {

    private final FeeCalculator calculator = new FeeCalculator();

    private PaymentOrder createOrder(String amount) {
        return new PaymentOrder(
                "P-1",
                1L,
                new BigDecimal(amount),
                Instant.parse("2026-11-10T10:00:00Z"),
                ZoneId.of("UTC")
        );
    }

    @Test
    void calculate_standardRate() {
        assertEquals(new BigDecimal("10.00"), calculator.calculate(createOrder("1000.00")));
        assertEquals(new BigDecimal("3.00"), calculator.calculate(createOrder("300.00")));
        assertEquals(new BigDecimal("5.00"), calculator.calculate(createOrder("500.00")));
    }

    @Test
    void calculate_maxCapAt30() {
        assertEquals(new BigDecimal("30.00"), calculator.calculate(createOrder("3000.00")));
        assertEquals(new BigDecimal("30.00"), calculator.calculate(createOrder("5000.00")));
    }

    @Test
    void calculate_halfEvenRounding() {
        // 100.50 * 0.01 = 1.005 -> к ближайшему чётному 1.00
        assertEquals(new BigDecimal("1.00"), calculator.calculate(createOrder("100.50")));
        // 101.50 * 0.01 = 1.015 -> к ближайшему чётному 1.02
        assertEquals(new BigDecimal("1.02"), calculator.calculate(createOrder("101.50")));
    }

    @Test
    void calculate_verySmallAmountRoundsToZero() {
        // 0.40 * 0.01 = 0.004 -> 0.00
        assertEquals(new BigDecimal("0.00"), calculator.calculate(createOrder("0.40")));
    }

    @Test
    void calculate_nullOrder_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(null));
    }
}
