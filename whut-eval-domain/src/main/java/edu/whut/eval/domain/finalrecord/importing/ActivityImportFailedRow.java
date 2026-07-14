package edu.whut.eval.domain.finalrecord.importing;

import java.util.Map;

public record ActivityImportFailedRow(
        Long rowNo,
        String code,
        String message,
        Map<String, String> rawValue
) {
}
