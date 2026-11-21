package com.fintech.reconciliation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.reconciliation.analitic.AnalyticReport;
import com.fintech.reconciliation.importer.ImportResult;
import com.fintech.reconciliation.importer.ImporterCsv;
import com.fintech.reconciliation.model.BankSettlement;
import com.fintech.reconciliation.model.FeeCalculator;
import com.fintech.reconciliation.model.PaymentOrder;
import com.fintech.reconciliation.model.ReconciliationRecord;
import com.fintech.reconciliation.service.AnalyticsService;
import com.fintech.reconciliation.service.ReconciliationService;
import com.fintech.reconciliation.service.ReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EndToEndReconciliationTest {

    @TempDir
    Path tempDir;

    @Test
    void fullPipeline_fromCsvFilesToParsedJsonReport() throws IOException {
        Path ordersCsv = tempDir.resolve("orders.csv");
        Path settlementsCsv = tempDir.resolve("settlements.csv");
        Path reportsDir = tempDir.resolve("reports");

        // 5 валидных заказов + 2 ошибки импорта
        Files.write(ordersCsv, List.of(
                "operationId;clientId;grossAmount;createdAtUtc;zoneId",
                "P-101;1;1000.00;2026-11-10T10:00:00Z;Europe/Berlin",  // MATCHED: 1000 - 10 = 990.00
                "P-102;1;300.00;2026-11-10T11:00:00Z;Europe/Berlin",   // AMOUNT_MISMATCH: ожидаем 297.00, факт 290.00
                "P-103;2;500.00;2026-11-10T12:00:00Z;Europe/Berlin",   // LATE: ожидаем 495.00, зачислено 13 ноя
                "P-104;2;800.00;2026-11-10T13:00:00Z;Europe/Berlin",   // MISSING_SETTLEMENT: ожидаем 792.00
                "P-105;3;200.00;2026-11-11T09:00:00Z;Europe/Berlin",   // MISSING_SETTLEMENT: ожидаем 198.00
                "P-101;1;1000.00;2026-11-10T10:00:00Z;Europe/Berlin",  // Ошибка: дубликат ID
                "invalid;line"                                         // Ошибка: неверное число колонок
        ), StandardCharsets.UTF_8);

        // 4 валидных зачисления + 2 ошибки импорта
        Files.write(settlementsCsv, List.of(
                "operationId;creditedAmount;settledAtUtc",
                "P-101;990.00;2026-11-11T12:00:00Z",
                "P-102;290.00;2026-11-11T12:00:00Z",
                "P-103;495.00;2026-11-13T10:00:00Z",
                "P-999;500.00;2026-11-12T10:00:00Z", // UNKNOWN_OPERATION
                "P-101;990.00;2026-11-11T12:00:00Z", // Ошибка: дубликат ID
                "P-888;-50.00;2026-11-12T10:00:00Z"  // Ошибка: отрицательная сумма
        ), StandardCharsets.UTF_8);

        // 1. Импорт CSV
        ImporterCsv importer = new ImporterCsv();
        ImportResult<PaymentOrder> ordersResult = importer.importOrders(ordersCsv);
        ImportResult<BankSettlement> settlementsResult = importer.importSettlements(settlementsCsv);

        assertEquals(5, ordersResult.apply().size());
        assertEquals(2, ordersResult.errors().size());
        assertEquals(4, settlementsResult.apply().size());
        assertEquals(2, settlementsResult.errors().size());

        // 2. Сверка с реальным FeeCalculator
        ReconciliationService reconciliationService = new ReconciliationService(new FeeCalculator());
        List<ReconciliationRecord> records = reconciliationService.reconcile(
                ordersResult.apply(),
                settlementsResult.apply()
        );

        // 3. Аналитика
        AnalyticReport analyticReport = new AnalyticsService(records).report();

        // 4. Формирование отчётов
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-20T08:44:00Z"), ZoneOffset.UTC);
        ReportService reportService = new ReportService(fixedClock);
        reportService.generateReport(
                reportsDir,
                analyticReport,
                records,
                ordersResult.errors(),
                settlementsResult.errors()
        );

        // 5. Обратное чтение JSON и проверка итоговых цифр из ТЗ
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(reportsDir.resolve("reconciliation.json").toFile());

        assertEquals("2026-11-20T08:44:00Z", root.get("generatedAtUtc").asText());

        JsonNode summary = root.get("summary");
        assertEquals(6, summary.get("totalRecords").asLong());
        assertEquals(1, summary.get("countsByStatus").get("MATCHED").asLong());
        assertEquals(1, summary.get("countsByStatus").get("AMOUNT_MISMATCH").asLong());
        assertEquals(1, summary.get("countsByStatus").get("LATE").asLong());
        assertEquals(2, summary.get("countsByStatus").get("MISSING_SETTLEMENT").asLong());
        assertEquals(1, summary.get("countsByStatus").get("UNKNOWN_OPERATION").asLong());
        assertEquals(0, summary.get("countsByStatus").get("DATE_CONFLICT").asLong());
        assertEquals("990.00", summary.get("totalMatchedAmount").asText());
        assertEquals("2772.00", summary.get("totalExpectedInternalAmount").asText());

        assertEquals(6, root.get("records").size());
    }
}
