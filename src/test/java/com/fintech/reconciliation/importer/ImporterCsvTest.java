package com.fintech.reconciliation.importer;

import com.fintech.reconciliation.model.BankSettlement;
import com.fintech.reconciliation.model.PaymentOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ImporterCsvTest {

    private final ImporterCsv csv = new ImporterCsv();
    @TempDir
     Path path;

    @Test
    void importSettlements_validAndDuplicates() throws IOException {
        Path file = path.resolve("settlements.csv");
        List<String> lines = List.of(
                "operationId;creditedAmount;settledAtUtc",
                "P-101;abc;2026-11-11T12:00:00Z", // нечисловая сумма -> в ошибки, ID не зарезервирован
                "P-101;990.00;2026-11-11T12:00:00Z", // первая корректная -> принята
                "", // пустая строка -> пропуск
                "P-101;990.00;2026-11-11T12:00:00Z" // дубликат -> ошибка
        );
        Files.write(file, lines, StandardCharsets.UTF_8);

        ImportResult<BankSettlement> result = csv.importSettlements(file);

        assertEquals(1, result.apply().size());
        assertEquals("P-101", result.apply().get(0).operationId());
        assertEquals(2, result.errors().size());
    }

    @Test
    void importOrders_invalidHeader_throwsIllegalArgumentException() throws IOException {
        Path file = path.resolve("bad_header.csv");
        Files.writeString(file, "wrong;header\nP-101;1;100.00;2026-11-10T10:00:00Z;UTC", StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> csv.importOrders(file));
    }

    @Test
    void importOrders_missingFile_throwsIOException() {;

        assertThrows(IOException.class, () -> csv.importOrders(Path.of("")));
    }


    @Test
    public void validSkipBlankAndContinueIfError()throws IOException {

        Path file = path.resolve("order.csv");
        List<String> orders = List.of(    "operationId;clientId;grossAmount;createdAtUtc;zoneId",
                "P-101;1;1000.00;2026-11-10T10:00:00Z;Europe/Berlin",
                "   ", // пустая строка пропускается без ошибки
                "P-102;1;-50.00;2026-11-10T11:00:00Z;Europe/Berlin", // ошибка: не резервирует ID P-102
                "P-102;1;300.00;2026-11-10T11:00:00Z;Europe/Berlin", // первая корректная для P-102 -> принята
                "P-101;1;1000.00;2026-11-10T10:00:00Z;Europe/Berlin", // дубль P-101 -> ошибка
                "broken;line" // неверное количество колонок -> ошибка
        );

        Files.write(file,orders, StandardCharsets.UTF_8);
        ImportResult<PaymentOrder>result = csv.importOrders(file);

        assertEquals(2, result.apply().size());
        assertEquals("P-101", result.apply().get(0).operationId());
        assertEquals("P-102", result.apply().get(1).operationId());
        assertEquals(new BigDecimal("300.00"), result.apply().get(1).grossAmount());

        assertEquals(3, result.errors().size());
        assertEquals(4, result.errors().get(0).lineNumber());
        assertEquals(6, result.errors().get(1).lineNumber());
        assertEquals(7, result.errors().get(2).lineNumber());
    }
}
