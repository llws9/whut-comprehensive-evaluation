package edu.whut.eval.domain.finalrecord.importing;

import java.util.Map;

public record LectureImportFailedRow(
        Long rowNo,
        String code,
        String message,
        Map<String, String> rawValue
) {
}
