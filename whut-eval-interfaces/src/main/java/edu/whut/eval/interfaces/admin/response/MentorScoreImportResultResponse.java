package edu.whut.eval.interfaces.admin.response;

import java.util.List;

public record MentorScoreImportResultResponse(
        String importBatchId,
        long totalCount,
        long successCount,
        long failedCount,
        List<MentorScoreImportFailedRowResponse> failedRows,
        String processedAt
) {
}
