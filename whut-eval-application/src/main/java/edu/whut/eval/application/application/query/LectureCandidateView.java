package edu.whut.eval.application.application.query;

import java.math.BigDecimal;

public record LectureCandidateView(
        Long lectureId,
        String title,
        String heldAt,
        String academicYear,
        BigDecimal maxScore,
        String attendanceStatus
) {
}
