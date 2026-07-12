package edu.whut.eval.application.finalrecord.importing;

import java.math.BigDecimal;

public record MentorScoreImportedComponent(
        Long rowNo,
        Long studentUserId,
        String academicYear,
        String categoryCode,
        String itemCode,
        BigDecimal scoreValue,
        String displayText,
        String sourceRefId
) {
}
