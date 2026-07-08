package edu.whut.eval.application.finalrecord.query;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminFinalRecordListItemView(
        Long finalRecordId,
        Long studentUserId,
        String studentUserNo,
        String studentUserName,
        Long orgUnitId,
        String orgUnitName,
        String academicYear,
        String status,
        BigDecimal moralTotal,
        BigDecimal intellectualTotal,
        BigDecimal physicalTotal,
        BigDecimal laborTotal,
        BigDecimal grandTotal,
        Instant submittedAt,
        Instant confirmedAt,
        Long version
) {
}
