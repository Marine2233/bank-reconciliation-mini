package com.fintech.reconciliation.model;

import com.fintech.reconciliation.model.enums.ReconciliationStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record ReconciliationRecord(String operationId,
                                   ReconciliationStatus status,
                                   BigDecimal expectedNet,
                                   BigDecimal actualNet,
                                   String details) {
    public ReconciliationRecord{
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("operationId не может быть null или пустым");
        }

        if (status == null) {
            throw new IllegalArgumentException("status не может быть null");
        }

        if (details == null || details.isBlank()) {
            throw new IllegalArgumentException("details не может быть null или пустым");
        }


        if (status != ReconciliationStatus.UNKNOWN_OPERATION && expectedNet == null) {
            throw new IllegalArgumentException("expectedNet обязателен для статуса " + status);
        }

        if (status != ReconciliationStatus.MISSING_SETTLEMENT && actualNet == null) {
            throw new IllegalArgumentException("actualNet обязателен для статуса " + status);
        }


        try {

            if (expectedNet != null) {
                expectedNet = expectedNet.setScale(2, RoundingMode.UNNECESSARY);
            }

            if (actualNet != null) {
                actualNet = actualNet.setScale(2, RoundingMode.UNNECESSARY);
            }

        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Денежные значения должны иметь точность до копеек", e);
        }
    }
}
