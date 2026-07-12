package edu.whut.eval.interfaces.admin.response;

import java.util.Map;

public record MentorScoreImportFailedRowResponse(
        Long rowNo,
        String code,
        String message,
        Map<String, String> rawValue
) {
}
