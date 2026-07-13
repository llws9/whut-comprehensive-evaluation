package edu.whut.eval.application.finalrecord.importing;

import java.math.BigDecimal;

public record ActivityImportedComponent(
        Long rowNo,
        Long studentUserId,
        String studentNo,
        String canonicalItemCode,
        String categoryCode,
        BigDecimal scoreValue,
        String rawDisplayText,
        String displayText,
        String activityBatchId
) {
}
