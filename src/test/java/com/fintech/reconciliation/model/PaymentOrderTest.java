package com.fintech.reconciliation.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PaymentOrderTest {

    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");



    @Test
    public void assertTrowsInvalidFieldsParam(){
        assertThrows(IllegalArgumentException.class, () ->
                new PaymentOrder(null, 1L, new BigDecimal("100.00"), NOW, ZONE));
        assertThrows(IllegalArgumentException.class, () ->
                new PaymentOrder("   ", 1L, new BigDecimal("100.00"), NOW, ZONE));
        assertThrows(IllegalArgumentException.class, () ->
                new PaymentOrder("P-101", 0L, new BigDecimal("100.00"), NOW, ZONE));
        assertThrows(IllegalArgumentException.class, () ->
                new PaymentOrder("P-101", -5L, new BigDecimal("100.00"), NOW, ZONE));
        assertThrows(IllegalArgumentException.class, () ->
                new PaymentOrder("P-101", 1L, null, NOW, ZONE));
        assertThrows(IllegalArgumentException.class, () ->
                new PaymentOrder("P-101", 1L, BigDecimal.ZERO, NOW, ZONE));
        assertThrows(IllegalArgumentException.class, () ->
                new PaymentOrder("P-101", 1L, new BigDecimal("-10.00"), NOW, ZONE));
        assertThrows(IllegalArgumentException.class, () ->
                new PaymentOrder("P-101", 1L, new BigDecimal("10.123"), NOW, ZONE));
        assertThrows(IllegalArgumentException.class, () ->
                new PaymentOrder("P-101", 1L, new BigDecimal("100.00"), null, ZONE));
        assertThrows(IllegalArgumentException.class, () ->
                new PaymentOrder("P-101", 1L, new BigDecimal("100.00"), NOW, null));

    }

    @Test
    public void validPaymentOrder(){
        PaymentOrder order = new PaymentOrder("1",1,new BigDecimal("100.00"), NOW, ZONE);
        assertEquals("1",order.operationId());
        assertEquals(1,order.clientId());
        assertEquals(new BigDecimal("100.00"),order.grossAmount());
        assertEquals(NOW,order.createdAtUtc());
        assertEquals(ZONE,order.clientZone());
    }
}
