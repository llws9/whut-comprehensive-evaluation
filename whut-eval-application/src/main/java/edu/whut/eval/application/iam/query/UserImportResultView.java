package edu.whut.eval.application.iam.query;

import java.util.List;

public record UserImportResultView(
        long totalCount,
        long successCount,
        long failedCount,
        List<UserImportFailedRowView> failedRows
) {
}
