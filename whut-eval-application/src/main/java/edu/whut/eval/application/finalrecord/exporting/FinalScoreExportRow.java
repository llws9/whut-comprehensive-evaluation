package edu.whut.eval.application.finalrecord.exporting;

import java.math.BigDecimal;
import java.time.Instant;

public record FinalScoreExportRow(
        Long finalRecordId,
        Long studentUserId,
        String studentUserNo,
        String studentUserName,
        String gradeCode,
        String gradeName,
        String classCode,
        String className,
        String academicYear,
        String status,
        BigDecimal moralTotal,
        BigDecimal intellectualTotal,
        BigDecimal physicalTotal,
        BigDecimal laborTotal,
        BigDecimal grandTotal,
        Instant submittedAt,
        Instant confirmedAt
) {
}
