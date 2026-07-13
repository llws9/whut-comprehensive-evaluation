package edu.whut.eval.interfaces.admin.response;

import java.math.BigDecimal;
import java.util.List;

public record ActivityImportResultResponse(
        String activityBatchId,
        String title,
        String itemCode,
        BigDecimal scoreValue,
        long totalCount,
        long successCount,
        long failedCount,
        List<ActivityImportFailedRowResponse> failedRows
) {
}
