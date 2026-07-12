package edu.whut.eval.application.finalrecord.importing;

import java.math.BigDecimal;

public record LectureImportedComponent(
        Long rowNo,
        Long studentUserId,
        String studentNo,
        String scoreValueText,
        BigDecimal scoreValue,
        String rawDisplayText,
        String displayText
) {
}
