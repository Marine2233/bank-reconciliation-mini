package com.fintech.reconciliation.service;

import com.fintech.reconciliation.model.BankSettlement;
import com.fintech.reconciliation.model.FeeCalculator;
import com.fintech.reconciliation.model.PaymentOrder;
import com.fintech.reconciliation.model.ReconciliationRecord;
import com.fintech.reconciliation.model.enums.ReconciliationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReconciliationServiceTest {

    @Mock
    private  FeeCalculator calculator;

    @InjectMocks
    private ReconciliationService service;


    private static final ZoneId CLIENT_ZONE = ZoneId.of("Europe/Berlin");

    @Test
    void reconcile_matchedAndVerifyCalculatorCall() {
        PaymentOrder order = new PaymentOrder("P-101", 1L, new BigDecimal("1000.00"),
                Instant.parse("2026-11-10T10:00:00Z"), CLIENT_ZONE);
        BankSettlement settlement = new BankSettlement("P-101", new BigDecimal("990.00"),
                Instant.parse("2026-11-11T12:00:00Z"));

        when(calculator.calculate(order)).thenReturn(new BigDecimal("10.00"));

        List<ReconciliationRecord> result = service.reconcile(List.of(order), List.of(settlement));

        assertEquals(1, result.size());
        assertEquals(ReconciliationStatus.MATCHED, result.get(0).status());
        assertEquals(new BigDecimal("990.00"), result.get(0).expectedNet());
        assertEquals(new BigDecimal("990.00"), result.get(0).actualNet());

        verify(calculator, times(1)).calculate(order);
        verifyNoMoreInteractions(calculator);
    }

    @Test
    void reconcile_amountMismatch() {
        PaymentOrder order = new PaymentOrder("P-102", 1L, new BigDecimal("300.00"),
                Instant.parse("2026-11-10T10:00:00Z"), CLIENT_ZONE);
        BankSettlement settlement = new BankSettlement("P-102", new BigDecimal("290.00"),
                Instant.parse("2026-11-11T10:00:00Z"));

        when(calculator.calculate(order)).thenReturn(new BigDecimal("3.00"));

        List<ReconciliationRecord> result = service.reconcile(List.of(order), List.of(settlement));

        assertEquals(1, result.size());
        assertEquals(ReconciliationStatus.AMOUNT_MISMATCH, result.get(0).status());
        assertEquals(new BigDecimal("297.00"), result.get(0).expectedNet());
        assertEquals(new BigDecimal("290.00"), result.get(0).actualNet());
        verify(calculator).calculate(order);
    }

    @Test
    void reconcile_lateSettlement() {
        // Создано 10 ноября, зачислено 13 ноября (просрочка на 1 день)
        PaymentOrder order = new PaymentOrder("P-103", 1L, new BigDecimal("500.00"),
                Instant.parse("2026-11-10T10:00:00Z"), CLIENT_ZONE);
        BankSettlement settlement = new BankSettlement("P-103", new BigDecimal("495.00"),
                Instant.parse("2026-11-13T00:01:00Z"));

        when(calculator.calculate(order)).thenReturn(new BigDecimal("5.00"));

        List<ReconciliationRecord> result = service.reconcile(List.of(order), List.of(settlement));

        assertEquals(1, result.size());
        assertEquals(ReconciliationStatus.LATE, result.get(0).status());
        verify(calculator).calculate(order);
    }

    @Test
    void reconcile_lastAllowedDay_isMatched() {
        // Создано 10 ноября, зачислено 12 ноября (23:50 по Берлину = 22:50 UTC) -> 2-й календарный день, вовремя
        PaymentOrder order = new PaymentOrder("P-104", 1L, new BigDecimal("500.00"),
                Instant.parse("2026-11-10T08:00:00Z"), CLIENT_ZONE);
        BankSettlement settlement = new BankSettlement("P-104", new BigDecimal("495.00"),
                Instant.parse("2026-11-12T22:50:00Z"));

        when(calculator.calculate(order)).thenReturn(new BigDecimal("5.00"));

        List<ReconciliationRecord> result = service.reconcile(List.of(order), List.of(settlement));

        assertEquals(1, result.size());
        assertEquals(ReconciliationStatus.MATCHED, result.get(0).status());
        verify(calculator).calculate(order);
    }

    @Test
    void reconcile_amountMismatchAndLate_prioritizesAmountMismatch() {
        // Одновременное нарушение суммы и срока -> приоритет AMOUNT_MISMATCH над LATE
        PaymentOrder order = new PaymentOrder("P-105", 1L, new BigDecimal("300.00"),
                Instant.parse("2026-11-10T10:00:00Z"), CLIENT_ZONE);
        BankSettlement settlement = new BankSettlement("P-105", new BigDecimal("280.00"),
                Instant.parse("2026-11-15T10:00:00Z"));

        when(calculator.calculate(order)).thenReturn(new BigDecimal("3.00"));

        List<ReconciliationRecord> result = service.reconcile(List.of(order), List.of(settlement));

        assertEquals(1, result.size());
        assertEquals(ReconciliationStatus.AMOUNT_MISMATCH, result.get(0).status());
        verify(calculator).calculate(order);
    }

    @Test
    void reconcile_dateConflict_settledBeforeCreated() {
        PaymentOrder order = new PaymentOrder("P-106", 1L, new BigDecimal("1000.00"),
                Instant.parse("2026-11-10T10:00:00Z"), CLIENT_ZONE);
        BankSettlement settlement = new BankSettlement("P-106", new BigDecimal("990.00"),
                Instant.parse("2026-11-10T09:59:59Z"));

        when(calculator.calculate(order)).thenReturn(new BigDecimal("10.00"));

        List<ReconciliationRecord> result = service.reconcile(List.of(order), List.of(settlement));

        assertEquals(1, result.size());
        assertEquals(ReconciliationStatus.DATE_CONFLICT, result.get(0).status());
        verify(calculator).calculate(order);
    }

    @Test
    void reconcile_missingSettlement() {
        PaymentOrder order = new PaymentOrder("P-107", 1L, new BigDecimal("1000.00"),
                Instant.parse("2026-11-10T10:00:00Z"), CLIENT_ZONE);

        when(calculator.calculate(order)).thenReturn(new BigDecimal("10.00"));

        List<ReconciliationRecord> result = service.reconcile(List.of(order), List.of());

        assertEquals(1, result.size());
        assertEquals(ReconciliationStatus.MISSING_SETTLEMENT, result.get(0).status());
        assertEquals(new BigDecimal("990.00"), result.get(0).expectedNet());
        assertNull(result.get(0).actualNet());
        verify(calculator).calculate(order);
    }

    @Test
    void reconcile_unknownOperation() {
        BankSettlement settlement = new BankSettlement("P-999", new BigDecimal("500.00"),
                Instant.parse("2026-11-11T10:00:00Z"));

        List<ReconciliationRecord> result = service.reconcile(List.of(), List.of(settlement));

        assertEquals(1, result.size());
        assertEquals(ReconciliationStatus.UNKNOWN_OPERATION, result.get(0).status());
        assertNull(result.get(0).expectedNet());
        assertEquals(new BigDecimal("500.00"), result.get(0).actualNet());
        verifyNoInteractions(calculator);
    }

    @Test
    void reconcile_emptyLists_returnsEmptyImmutableList() {
        List<ReconciliationRecord> result = service.reconcile(List.of(), List.of());

        assertTrue(result.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> result.add(null));
        verifyNoInteractions(calculator);
    }

    @Test
    void reconcile_duplicateOrderIds_throwsIllegalArgumentException() {
        PaymentOrder order1 = new PaymentOrder("P-101", 1L, new BigDecimal("100.00"),
                Instant.parse("2026-11-10T10:00:00Z"), CLIENT_ZONE);
        PaymentOrder order2 = new PaymentOrder("P-101", 2L, new BigDecimal("200.00"),
                Instant.parse("2026-11-10T11:00:00Z"), CLIENT_ZONE);

        assertThrows(IllegalArgumentException.class, () ->
                service.reconcile(List.of(order1, order2), List.of()));
        verifyNoInteractions(calculator);
    }

    @Test
    void reconcile_duplicateSettlementIds_throwsIllegalArgumentException() {
        BankSettlement settlement1 = new BankSettlement("P-101", new BigDecimal("100.00"),
                Instant.parse("2026-11-11T10:00:00Z"));
        BankSettlement settlement2 = new BankSettlement("P-101", new BigDecimal("100.00"),
                Instant.parse("2026-11-11T11:00:00Z"));

        assertThrows(IllegalArgumentException.class, () ->
                service.reconcile(List.of(), List.of(settlement1, settlement2)));
        verifyNoInteractions(calculator);
    }

}
