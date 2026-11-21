package com.fintech.reconciliation.analitic;

import com.fintech.reconciliation.model.enums.ReconciliationStatus;

import java.math.BigDecimal;
import java.util.Map;

public record AnalyticReport( long totalRecords,
                              Map<ReconciliationStatus, Long> countsByStatus,
                             BigDecimal totalMatchedAmount,
                             BigDecimal totalExpectedInternalAmount) {

    public AnalyticReport {
        if (totalRecords < 0) {
            throw new IllegalArgumentException("totalRecords не может быть отрицательным");
        }
        if (totalMatchedAmount == null || totalExpectedInternalAmount == null) {
            throw new IllegalArgumentException("Денежные суммы не могут быть null");
        }

        if (countsByStatus == null){
            throw new IllegalArgumentException("map is null.");
        }

        countsByStatus = Map.copyOf(countsByStatus);

    }
}
