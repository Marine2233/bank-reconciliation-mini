package com.fintech.reconciliation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fintech.reconciliation.analitic.AnalyticReport;
import com.fintech.reconciliation.importer.ImportError;
import com.fintech.reconciliation.model.ReconciliationRecord;
import com.fintech.reconciliation.model.enums.ReconciliationStatus;
import com.fintech.reconciliation.report.ReconciliationReport;
import com.fintech.reconciliation.report.dto.ReconciliationDTO;
import com.fintech.reconciliation.report.dto.SummaryDTO;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ReportService {
    private final Clock clock;
    private final ObjectMapper mapper;

    public ReportService(Clock clock){
        if (clock == null){
            throw new IllegalArgumentException("clock is null.");
        }
        this.clock = clock;
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    public void generateReport(
            Path outputDirectory,
            AnalyticReport analyticReport,
            List<ReconciliationRecord> records,
            List<ImportError> ordersErrors,
            List<ImportError> settlementsErrors) throws IOException {

        if (outputDirectory == null || analyticReport == null || records == null
                || ordersErrors == null || settlementsErrors == null) {
            throw new IllegalArgumentException("Параметры генерации отчётов не могут быть null");
        }

        Files.createDirectories(outputDirectory);

        List<ReconciliationRecord> immutableRecords = List.copyOf(records);
        List<ImportError> immutableOrdersErrors = List.copyOf(ordersErrors);
        List<ImportError> immutableSettlementsErrors = List.copyOf(settlementsErrors);

        try (ExecutorService service = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<Void> futureReportSummary = CompletableFuture.runAsync(() -> {
                try {
                    generateTxtSummary(
                            outputDirectory.resolve("summary.txt"),
                            analyticReport,
                            immutableOrdersErrors,
                            immutableSettlementsErrors
                    );
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }, service);

            CompletableFuture<Void> futureReportJson = CompletableFuture.runAsync(() -> {
                try {
                    generateJsonReport(outputDirectory, analyticReport, immutableRecords);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }, service);

            CompletableFuture.allOf(futureReportSummary, futureReportJson).join();

        } catch (CompletionException e) {
            switch (e.getCause()){
                case UncheckedIOException uncheckedIOException -> throw uncheckedIOException.getCause();
                case RuntimeException exception -> throw exception;
                case  null, default -> throw new IOException("Ошибка формирования отчета.");

            }
        }
    }

    private void generateJsonReport(Path outputDirectory,AnalyticReport analyticReport,List<ReconciliationRecord>reconciliation) throws IOException {

        Instant now = Instant.now(clock);
        SummaryDTO summaryDTO = SummaryDTO.mapToDto(analyticReport);
        List<ReconciliationDTO> reconciliationDTO = List.copyOf(reconciliation).stream().map(ReconciliationDTO::mapToDto).toList();
        ReconciliationReport report = new ReconciliationReport(now, summaryDTO, reconciliationDTO);

        mapper.writeValue(outputDirectory.resolve(Path.of("reconciliation.json")).toFile(),report);

    }

    private void generateTxtSummary(
            Path txtFile,
            AnalyticReport analyticReport,
            List<ImportError> ordersErrors,
            List<ImportError> settlementsErrors
    ) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(txtFile, StandardCharsets.UTF_8)) {
            writer.write("BANK RECONCILIATION REPORT");
            writer.newLine();
            writer.write("Проверено результатов: " + analyticReport.totalRecords());
            writer.newLine();

            for (ReconciliationStatus status : ReconciliationStatus.values()) {
                long count = analyticReport.countsByStatus().getOrDefault(status, 0L);
                writer.write(status.name() + ": " + count);
                writer.newLine();
            }

            writer.write("Подтверждённая сумма: " + analyticReport.totalMatchedAmount().toPlainString());
            writer.newLine();
            writer.write("Ожидаемая сумма: " + analyticReport.totalExpectedInternalAmount().toPlainString());
            writer.newLine();
            writer.write("Ошибок импорта orders.csv: " + ordersErrors.size());
            writer.newLine();
            writer.write("Ошибок импорта settlements.csv: " + settlementsErrors.size());
            writer.newLine();

            writer.newLine();
            writer.write("Ошибки импорта orders.csv:");
            writer.newLine();
            writeErrorsSection(writer, ordersErrors);

            writer.newLine();
            writer.write("Ошибки импорта settlements.csv:");
            writer.newLine();
            writeErrorsSection(writer, settlementsErrors);
        }
    }

    private void writeErrorsSection(BufferedWriter writer, List<ImportError> errors) throws IOException {
        if (errors.isEmpty()) {
            writer.write("  Ошибок нет");
            writer.newLine();
            return;
        }
        for (ImportError error : errors) {
            writer.write("  Строка " + error.lineNumber() + ": " + error.reason());
            writer.newLine();
        }
    }

}
