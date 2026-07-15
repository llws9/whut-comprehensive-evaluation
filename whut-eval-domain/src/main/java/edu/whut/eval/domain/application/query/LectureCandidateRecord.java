package edu.whut.eval.domain.application.query;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LectureCandidateRecord(
        Long lectureId,
        String title,
        String academicYear,
        BigDecimal maxScore,
        String sourceRefId,
        LocalDateTime createdAt
) {
}
