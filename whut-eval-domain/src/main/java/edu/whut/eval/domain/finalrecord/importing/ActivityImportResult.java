package edu.whut.eval.domain.finalrecord.importing;

import java.math.BigDecimal;
import java.util.List;

public record ActivityImportResult(
        String activityBatchId,
        String title,
        String itemCode,
        BigDecimal scoreValue,
        long totalCount,
        long successCount,
        long failedCount,
        List<ActivityImportFailedRow> failedRows
) {
}
