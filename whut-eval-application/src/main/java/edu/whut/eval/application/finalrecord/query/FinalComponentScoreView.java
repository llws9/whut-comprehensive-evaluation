package edu.whut.eval.application.finalrecord.query;

import java.math.BigDecimal;
import java.time.Instant;

public record FinalComponentScoreView(
        Long id,
        Long finalRecordId,
        String categoryCode,
        String itemCode,
        String itemName,
        BigDecimal scoreValue,
        String displayText,
        String sourceType,
        String sourceRefId,
        Instant createdAt
) {
}
