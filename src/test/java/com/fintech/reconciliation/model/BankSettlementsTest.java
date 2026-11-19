package com.fintech.reconciliation.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
//

public class BankSettlementsTest {

    private final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");

    @Test
    public void assertNotValidFields(){
        assertThrows(IllegalArgumentException.class, () ->
                new BankSettlement(null, new BigDecimal("990.00"), NOW));
        assertThrows(IllegalArgumentException.class, () ->
                new BankSettlement("", new BigDecimal("990.00"), NOW));
        assertThrows(IllegalArgumentException.class, () ->
                new BankSettlement("P-101", null, NOW));
        assertThrows(IllegalArgumentException.class, () ->
                new BankSettlement("P-101", BigDecimal.ZERO, NOW));
        assertThrows(IllegalArgumentException.class, () ->
                new BankSettlement("P-101", new BigDecimal("-50.00"), NOW));
        assertThrows(IllegalArgumentException.class, () ->
                new BankSettlement("P-101", new BigDecimal("990.005"), NOW));
        assertThrows(IllegalArgumentException.class, () ->
                new BankSettlement("P-101", new BigDecimal("990.00"), null));

    }

    @Test
    public void assertValidFields(){
        BankSettlement settlement = new BankSettlement("101",new BigDecimal("100.00"),NOW);
        assertEquals("101",settlement.operationId());
        assertEquals(new BigDecimal("100.00"),settlement.creditedAmount());
        assertEquals(NOW,settlement.settledAtUtc());
    }

}
