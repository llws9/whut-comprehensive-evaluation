package edu.whut.eval.application.finalrecord.query;

import edu.whut.eval.domain.finalrecord.model.FinalRecordStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record FinalRecordStudentView(
        Long finalRecordId,
        Long studentUserId,
        String academicYear,
        FinalRecordStatus status,
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
