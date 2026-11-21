package com.fintech.reconciliation.service;

import com.fintech.reconciliation.analitic.AnalyticReport;
import com.fintech.reconciliation.model.ReconciliationRecord;
import com.fintech.reconciliation.model.enums.ReconciliationStatus;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class AnalyticsService {

    private final List<ReconciliationRecord> reconciliationRecord;

    public AnalyticsService(List<ReconciliationRecord> reconciliationRecord){

        if (reconciliationRecord == null || reconciliationRecord.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Список записей сверки не может быть null");
        }
        this.reconciliationRecord = List.copyOf(reconciliationRecord);
    }

    public AnalyticReport report(){

        long totalReconciliation = reconciliationRecord.size();
        Map<ReconciliationStatus,Long>countOperationByStatus = new HashMap<>();

        for (ReconciliationStatus status: ReconciliationStatus.values()){
            countOperationByStatus.putIfAbsent(status,0L);
        }
        countOperationByStatus.putAll(reconciliationRecord.stream().collect(Collectors.groupingBy(ReconciliationRecord::status,Collectors.counting())));

        BigDecimal totalMatched = reconciliationRecord
                .stream()
                .filter(reconciliationRecord-> reconciliationRecord.status().equals(ReconciliationStatus.MATCHED))
                .map(ReconciliationRecord::actualNet)
                .reduce(new BigDecimal("0.00"),BigDecimal::add);

        BigDecimal totalExpected = reconciliationRecord.stream()
                .filter(r-> !r.status().equals(ReconciliationStatus.UNKNOWN_OPERATION))
                .map(ReconciliationRecord::expectedNet)
                .reduce(new BigDecimal("0.00"),BigDecimal::add);

        return new AnalyticReport(totalReconciliation,Map.copyOf(countOperationByStatus),totalMatched,totalExpected);
    }

}
