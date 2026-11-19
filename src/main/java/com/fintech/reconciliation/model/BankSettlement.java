package com.fintech.reconciliation.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public record BankSettlement(String operationId,
                             BigDecimal creditedAmount,
                             Instant settledAtUtc) {
    public BankSettlement{
        if (operationId == null || operationId.trim().isBlank()){
            throw new IllegalArgumentException("operation id is null.");
        }
        if (creditedAmount == null || creditedAmount.compareTo(BigDecimal.ZERO) <= 0){
            throw new IllegalArgumentException("credit amount is null.");
        }
        if (settledAtUtc == null){
            throw new IllegalArgumentException("time by utc is null.");
        }

        try{
            creditedAmount = creditedAmount.setScale(2, RoundingMode.UNNECESSARY);
        }catch (Exception e){
            throw new IllegalArgumentException("сумма с лишними знаками после запятой: " + creditedAmount);
        }
    }
}
