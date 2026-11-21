package com.fintech.reconciliation.report.dto;

import com.fintech.reconciliation.model.ReconciliationRecord;

public record ReconciliationDTO(String operationId,
                                String status,
                                String expectedNet,
                                String actualNet,
                                String details) {

    public static ReconciliationDTO mapToDto(ReconciliationRecord record){
        return new ReconciliationDTO(
                record.operationId(),
                record.status().toString(),
                record.expectedNet() != null ? record.expectedNet().toPlainString() : null,
                record.actualNet() != null ? record.actualNet().toPlainString() : null,
                record.details());
    }
}
