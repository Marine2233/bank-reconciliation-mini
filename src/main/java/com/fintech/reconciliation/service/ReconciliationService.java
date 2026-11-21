package com.fintech.reconciliation.service;

import com.fintech.reconciliation.model.BankSettlement;
import com.fintech.reconciliation.model.FeeCalculator;
import com.fintech.reconciliation.model.PaymentOrder;
import com.fintech.reconciliation.model.ReconciliationRecord;
import com.fintech.reconciliation.model.enums.ReconciliationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public final class ReconciliationService {

    private final FeeCalculator calculator;

    public ReconciliationService(FeeCalculator calculator) {
        if (calculator == null) {
            throw new IllegalArgumentException("feeCalculator не может быть null");
        }
        this.calculator = calculator;
    }

    public List<ReconciliationRecord> reconcile(
            List<PaymentOrder> orders,
            List<BankSettlement> settlements) {

        if (orders == null || settlements == null) {
            throw new IllegalArgumentException("Параметры не могут быть null.");
        }

        Map<String,PaymentOrder> orderMap =mapById(orders);
        Map<String,BankSettlement> bankSettlementMap = mapById(settlements);
        List<ReconciliationRecord> reconciliationRecords = new ArrayList<>();

        for (PaymentOrder order: orders){
            BigDecimal commission = calculator.calculate(order);
            BigDecimal expectedNet = order.grossAmount().subtract(commission);

            BankSettlement settlement = bankSettlementMap.get(order.operationId());
            if (settlement== null){
                reconciliationRecords.add(new ReconciliationRecord(order.operationId(),
                        ReconciliationStatus.MISSING_SETTLEMENT,
                        expectedNet,null,
                        "Банковское зачисление не найдено."));
                continue;
            }

            BigDecimal actualNet = settlement.creditedAmount();

            if (settlement.settledAtUtc().isBefore(order.createdAtUtc())){
                reconciliationRecords.add(new ReconciliationRecord(order.operationId(),
                        ReconciliationStatus.DATE_CONFLICT,
                        expectedNet,
                        actualNet,"Время зачисления: " + settlement.settledAtUtc() + " раньше создания поручения-" + order.createdAtUtc()));
                continue;
            }

            if (expectedNet.compareTo(actualNet) != 0){
                reconciliationRecords.add(new ReconciliationRecord(order.operationId(),
                        ReconciliationStatus.AMOUNT_MISMATCH,expectedNet,actualNet,"Ожидалось- " + expectedNet + ", фактически- " + actualNet));
                continue;
            }

            LocalDate create = order.createdAtUtc().atZone(order.clientZone()).toLocalDate();
            LocalDate bankDay = settlement.settledAtUtc().atZone(order.clientZone()).toLocalDate();
            LocalDate maxBankDate = create.plusDays(2);

            if (bankDay.isAfter(maxBankDate)){
                reconciliationRecords.add(new ReconciliationRecord(order.operationId(),ReconciliationStatus.LATE,
                        expectedNet,actualNet,"Зачислено: " + bankDay + ", допустимый срок до " + maxBankDate + " включительно."));
                continue;
            }

            reconciliationRecords.add(new ReconciliationRecord(order.operationId(),ReconciliationStatus.MATCHED,expectedNet,actualNet,"Операция успешна."));
        }

        for (BankSettlement settlement: settlements){
            if (!orderMap.containsKey(settlement.operationId())){
                reconciliationRecords.add(new ReconciliationRecord(settlement.operationId(),ReconciliationStatus.UNKNOWN_OPERATION,
                        null,
                        settlement.creditedAmount(),"Unknow operation."));
            }
        }

        reconciliationRecords.sort(Comparator.comparing(ReconciliationRecord::operationId));
        return List.copyOf(reconciliationRecords);
    }


    private <T> Map<String,T>mapById(List<T>list) {

        Map<String, T> result = new HashMap<>();
        for (T item : list) {
            if (item == null) {
                throw new IllegalArgumentException("Элемент списка не может быть null");
            }

            String id = switch (item) {
                case PaymentOrder order -> order.operationId();
                case BankSettlement settlement -> settlement.operationId();
                default -> throw new IllegalArgumentException("Неизвестный тип элемента: " + item.getClass());
            };

            if (result.put(id, item) != null) {
                throw new IllegalArgumentException("Дубликат operationId: " + id);
            }
        }
        return result;
    }
}
