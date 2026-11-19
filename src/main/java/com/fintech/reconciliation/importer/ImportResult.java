package com.fintech.reconciliation.importer;
import java.util.List;

public record ImportResult<T>(List<T> apply, List<ImportError> errors) {
    public ImportResult {

        apply = apply == null ? List.of() :List.copyOf(apply);
        errors = errors == null ?List.of() : List.copyOf(errors);
    }
}
