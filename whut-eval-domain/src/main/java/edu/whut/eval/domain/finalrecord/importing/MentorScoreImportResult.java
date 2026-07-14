package edu.whut.eval.domain.finalrecord.importing;

import java.time.Instant;
import java.util.List;

public record MentorScoreImportResult(
        String importBatchId,
        long totalCount,
        long successCount,
        long failedCount,
        List<MentorScoreImportFailedRow> failedRows,
        Instant processedAt
) {
}
