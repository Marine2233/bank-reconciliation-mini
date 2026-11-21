package com.fintech.reconciliation.report;

import com.fintech.reconciliation.report.dto.ReconciliationDTO;
import com.fintech.reconciliation.report.dto.SummaryDTO;

import java.time.Instant;
import java.util.List;

public record ReconciliationReport(Instant generatedAtUtc, SummaryDTO summary, List<ReconciliationDTO>records) {

}
