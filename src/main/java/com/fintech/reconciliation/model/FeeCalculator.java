package com.fintech.reconciliation.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class FeeCalculator {

    private static final BigDecimal RATE = new BigDecimal("0.01");
    private static final BigDecimal MAX_COMMISSION = new BigDecimal("30.00");

    public BigDecimal calculate(PaymentOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("Платежное поручение не может быть null.");
        }

        BigDecimal commission = order.grossAmount()
                .multiply(RATE)
                .setScale(2, RoundingMode.HALF_EVEN);

        return commission.compareTo(MAX_COMMISSION) > 0 ? MAX_COMMISSION : commission;
    }
}
