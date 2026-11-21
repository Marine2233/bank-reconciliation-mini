package com.fintech.reconciliation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.reconciliation.analitic.AnalyticReport;
import com.fintech.reconciliation.importer.ImportError;
import com.fintech.reconciliation.model.ReconciliationRecord;
import com.fintech.reconciliation.model.enums.ReconciliationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReportServiceTest {
    private static final Instant FIXED_TIME = Instant.parse("2026-11-20T08:44:00Z");
    private final Clock fixedClock = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);
    private final ReportService reportService = new ReportService(fixedClock);
    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    private Path path;

    @Test
    void generateReport_createsDirectoryAndWritesValidJsonAndTxt() throws IOException {
        Path reportsDir = path.resolve("nested/reports");

        List<ReconciliationRecord> records = List.of(
                new ReconciliationRecord("P-101", ReconciliationStatus.MATCHED,
                        new BigDecimal("990.00"), new BigDecimal("990.00"), "Операция успешна."),
                new ReconciliationRecord("P-102", ReconciliationStatus.MISSING_SETTLEMENT,
                        new BigDecimal("297.00"), null, "Банковское зачисление не найдено.")
        );
        AnalyticReport analyticReport = new AnalyticsService(records).report();
        List<ImportError> ordersErrors = List.of(new ImportError(5, "bad;row", "Неверное количество колонок"));
        List<ImportError> settlementsErrors = List.of(new ImportError(3, "P-99;-5;2026", "Отрицательная сумма"));

        reportService.generateReport(reportsDir, analyticReport, records, ordersErrors, settlementsErrors);

        Path jsonFile = reportsDir.resolve("reconciliation.json");
        Path txtFile = reportsDir.resolve("summary.txt");

        assertTrue(Files.isDirectory(reportsDir));
        assertTrue(Files.exists(jsonFile));
        assertTrue(Files.exists(txtFile));

        // Проверка JSON
        JsonNode root = mapper.readTree(jsonFile.toFile());
        assertEquals("2026-11-20T08:44:00Z", root.get("generatedAtUtc").asText());

        JsonNode summary = root.get("summary");
        assertEquals(2, summary.get("totalRecords").asLong());
        assertEquals(1, summary.get("countsByStatus").get("MATCHED").asLong());
        assertEquals(1, summary.get("countsByStatus").get("MISSING_SETTLEMENT").asLong());
        assertEquals("990.00", summary.get("totalMatchedAmount").asText());
        assertEquals("1287.00", summary.get("totalExpectedInternalAmount").asText());

        JsonNode jsonRecords = root.get("records");
        assertEquals(2, jsonRecords.size());
        assertEquals("P-101", jsonRecords.get(0).get("operationId").asText());
        assertEquals("MATCHED", jsonRecords.get(0).get("status").asText());
        assertEquals("990.00", jsonRecords.get(0).get("expectedNet").asText());
        assertEquals("990.00", jsonRecords.get(0).get("actualNet").asText());

        assertEquals("P-102", jsonRecords.get(1).get("operationId").asText());
        assertEquals("MISSING_SETTLEMENT", jsonRecords.get(1).get("status").asText());
        assertEquals("297.00", jsonRecords.get(1).get("expectedNet").asText());
        assertTrue(jsonRecords.get(1).get("actualNet").isNull());

        // Проверка TXT
        String txt = Files.readString(txtFile, StandardCharsets.UTF_8);
        assertTrue(txt.contains("BANK RECONCILIATION REPORT"));
        assertTrue(txt.contains("Проверено результатов: 2"));
        assertTrue(txt.contains("MATCHED: 1"));
        assertTrue(txt.contains("MISSING_SETTLEMENT: 1"));
        assertTrue(txt.contains("Подтверждённая сумма: 990.00"));
        assertTrue(txt.contains("Ожидаемая сумма: 1287.00"));
        assertTrue(txt.contains("Ошибок импорта orders.csv: 1"));
        assertTrue(txt.contains("Ошибок импорта settlements.csv: 1"));
        assertTrue(txt.contains("Строка 5: Неверное количество колонок"));
        assertTrue(txt.contains("Строка 3: Отрицательная сумма"));
    }

    @Test
    void generateReport_emptyStatistics_writesZerosAndNoErrors() throws IOException {
        Path reportsDir = path.resolve("empty_reports");
        AnalyticReport emptyReport = new AnalyticsService(List.of()).report();

        reportService.generateReport(reportsDir, emptyReport, List.of(), List.of(), List.of());

        JsonNode root = mapper.readTree(reportsDir.resolve("reconciliation.json").toFile());
        assertEquals(0, root.get("summary").get("totalRecords").asLong());
        assertEquals("0.00", root.get("summary").get("totalMatchedAmount").asText());
        assertEquals("0.00", root.get("summary").get("totalExpectedInternalAmount").asText());
        assertTrue(root.get("records").isEmpty());

        String txt = Files.readString(reportsDir.resolve("summary.txt"), StandardCharsets.UTF_8);
        assertTrue(txt.contains("Проверено результатов: 0"));
        assertTrue(txt.contains("Подтверждённая сумма: 0.00"));
        assertTrue(txt.contains("Ожидаемая сумма: 0.00"));
        assertTrue(txt.contains("Ошибок импорта orders.csv: 0"));
        assertTrue(txt.contains("Ошибок импорта settlements.csv: 0"));
        assertTrue(txt.contains("Ошибок нет"));
    }
}
