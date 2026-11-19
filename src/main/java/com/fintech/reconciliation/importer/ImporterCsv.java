package com.fintech.reconciliation.importer;

import com.fintech.reconciliation.model.BankSettlement;
import com.fintech.reconciliation.model.PaymentOrder;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ImporterCsv {

    private final String HEADER_ORDERS = "operationId;clientId;grossAmount;createdAtUtc;zoneId";
    private final String HEADER_SETTLEMENTS = "operationId;creditedAmount;settledAtUtc";

    public ImportResult<PaymentOrder> importOrders(Path path)throws IOException{

        if (path == null){
            throw new IllegalArgumentException("directory not found.");
        }

        String header;
        int numLine = 0;

        List<PaymentOrder>orders = new ArrayList<>();
        List<ImportError>errors = new ArrayList<>();
        Set<String>uniqueIds = new HashSet<>();

        try(BufferedReader reader = Files.newBufferedReader(path,StandardCharsets.UTF_8)){
            header = reader.readLine();
            numLine++;

            if (header == null || !header.trim().equals(HEADER_ORDERS)){
                throw new IllegalArgumentException("Ошибка структуры файла.");

            }

            String line;
            while ((line = reader.readLine()) != null){
                numLine++;

                if (line.isBlank()){
                    continue;
                }

                String[]parts = line.split(";",-1);

                if (parts.length != 5){
                    errors.add(new ImportError(numLine,line,"Битая строка."));
                    continue;
                }

                try{

                    String opId = parts[0].trim();
                    long cliId = Long.parseLong(parts[1].trim());
                    BigDecimal grossAmount = new BigDecimal(parts[2].trim());
                    Instant createAtUtc = Instant.parse(parts[3].trim());
                    ZoneId zoneId = ZoneId.of(parts[4].trim());

                    PaymentOrder order = new PaymentOrder(opId,cliId,grossAmount,createAtUtc,zoneId);

                    if (!uniqueIds.add(opId)){
                        errors.add(new ImportError(numLine,line,"Данный Id уже есть в системе."));
                    }else {
                        orders.add(order);
                    }

                } catch (Exception e) {
                    errors.add(new ImportError(numLine,line,"Ошибка парсинга."));
                }

            }
        }
        return new ImportResult<>(orders,errors);
    }
    public ImportResult<BankSettlement> importSettlements(Path path)throws IOException{

        if (path == null){
            throw new IllegalArgumentException("directory not found.");
        }

        List<BankSettlement>bankSettlements = new ArrayList<>();
        List<ImportError>errors = new ArrayList<>();
        Set<String>uniqueId = new HashSet<>();
        int numLine =0;
        String header = "";

        try(BufferedReader reader = Files.newBufferedReader(path,StandardCharsets.UTF_8)){
            header = reader.readLine();
            numLine++;

            if (header == null || !header.trim().equals(HEADER_SETTLEMENTS)){
                throw new IllegalArgumentException("Неверный формат заголовка.");
            }

            String line = "";
            while ((line = reader.readLine()) != null){
                numLine++;

                if (line.isBlank()){
                    continue;
                }

                String[]parts = line.split(";",-1);
                if (parts.length != 3){
                    errors.add(new ImportError(numLine,line,"Грязная строка."));
                    continue;
                }

                try {

                    String opId = parts[0].trim();
                    BigDecimal creditAmount = new BigDecimal(parts[1].trim());
                    Instant settledAtUtc = Instant.parse(parts[2].trim());

                    BankSettlement bankSettlement = new BankSettlement(opId,creditAmount,settledAtUtc);

                    if (!uniqueId.add(opId)){
                        errors.add(new ImportError(numLine,line,"Id в системе уже существует."));
                    }else {
                        bankSettlements.add(bankSettlement);
                    }
                } catch (Exception e) {
                    errors.add(new ImportError(numLine,line,"Ошибка парсинга."));
                }

            }
        }
        return new ImportResult<>(bankSettlements,errors);
    }
}
