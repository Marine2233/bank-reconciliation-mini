package com.fintech.reconciliation.report.dto;

import com.fintech.reconciliation.analitic.AnalyticReport;

import java.util.Map;
import java.util.stream.Collectors;

public record SummaryDTO(
        long totalRecords,
        Map<String, Long> countsByStatus,
        String totalMatchedAmount,
        String totalExpectedInternalAmount) {

    public  static SummaryDTO mapToDto(AnalyticReport report){

        Map<String,Long>countByStatus = report.countsByStatus()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(e->
                        e.getKey().name(),
                        Map.Entry::getValue));

        return new SummaryDTO(report.totalRecords(),countByStatus,report.totalMatchedAmount().toString(),report.totalExpectedInternalAmount().toString());
    }
}
