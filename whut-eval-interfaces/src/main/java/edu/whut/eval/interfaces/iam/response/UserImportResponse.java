package edu.whut.eval.interfaces.iam.response;

import java.util.List;

public record UserImportResponse(
        long totalCount,
        long successCount,
        long failedCount,
        List<UserImportFailedRowResponse> failedRows
) {
}
