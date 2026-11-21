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

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

public class Main {
    private static final Path DEFAULT_ORDERS_PATH = Path.of("data", "orders.csv");
    private static final Path DEFAULT_SETTLEMENTS_PATH = Path.of("data", "settlements.csv");
    private static final Path DEFAULT_REPORTS_DIR = Path.of("reports");
    public static void main(String[] args) {

        try {
            ImporterCsv importer = new ImporterCsv();
            ImportResult<PaymentOrder> ordersResult = importer.importOrders(DEFAULT_ORDERS_PATH);
            ImportResult<BankSettlement> settlementsResult = importer.importSettlements(DEFAULT_SETTLEMENTS_PATH);

            ReconciliationService reconciliationService = new ReconciliationService(new FeeCalculator());
            List<ReconciliationRecord> records = reconciliationService.reconcile(
                    ordersResult.apply(),
                    settlementsResult.apply()
            );

            AnalyticsService analyticsService = new AnalyticsService(records);
            AnalyticReport analyticReport = analyticsService.report();

            ReportService reportService = new ReportService(Clock.systemUTC());
            reportService.generateReport(
                    DEFAULT_REPORTS_DIR,
                    analyticReport,
                    records,
                    ordersResult.errors(),
                    settlementsResult.errors()
            );

            System.out.println("Сверка успешно завершена. Отчёты сохранены в каталог: " + DEFAULT_REPORTS_DIR.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Ошибка ввода-вывода при чтении CSV или записи отчётов: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Критическая ошибка выполнения сверки: " + e.getMessage());
            System.exit(2);
        }
    }

    }