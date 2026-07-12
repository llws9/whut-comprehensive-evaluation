package edu.whut.eval.interfaces.admin.response;

import java.util.List;

public record LectureImportResultResponse(
        String lectureBatchId,
        String title,
        String heldAt,
        String academicYear,
        long totalCount,
        long successCount,
        long failedCount,
        List<LectureImportFailedRowResponse> failedRows
) {
}
