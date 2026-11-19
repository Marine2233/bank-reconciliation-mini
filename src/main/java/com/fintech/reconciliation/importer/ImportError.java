package com.fintech.reconciliation.importer;

public record ImportError(int lineNumber,
                          String rawLine,
                          String reason) {
}
