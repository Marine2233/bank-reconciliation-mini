package com.fintech.reconciliation.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;

public record PaymentOrder(String operationId,
                           long clientId,
                           BigDecimal grossAmount,
                           Instant createdAtUtc,
                           ZoneId clientZone
) {
    public PaymentOrder {
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("Id не может быть null.");
        }
        if (clientId == 0 || clientId < 0) {
            throw new IllegalArgumentException("ClientId не может быть null.");
        }
        if (grossAmount == null || grossAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid param gross amount.");
        }
        if (createdAtUtc == null) {
            throw new IllegalArgumentException("date is null.");
        }
        if (clientZone == null) {
            throw new IllegalArgumentException("clientZone is null.");
        }
        try {
            grossAmount = grossAmount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (Exception e) {
            throw new IllegalArgumentException("Лишние знаки после запятой " + grossAmount);
        }
    }
}
