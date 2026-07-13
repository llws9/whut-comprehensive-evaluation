package edu.whut.eval.interfaces.admin.response;

import java.util.Map;

public record LectureImportFailedRowResponse(
        Long rowNo,
        String code,
        String message,
        Map<String, String> rawValue
) {
}
