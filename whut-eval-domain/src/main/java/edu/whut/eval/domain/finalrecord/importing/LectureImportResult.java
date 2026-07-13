package edu.whut.eval.domain.finalrecord.importing;

import java.time.LocalDateTime;
import java.util.List;

public record LectureImportResult(
        String lectureBatchId,
        String title,
        LocalDateTime heldAt,
        String academicYear,
        long totalCount,
        long successCount,
        long failedCount,
        List<LectureImportFailedRow> failedRows
) {
}
