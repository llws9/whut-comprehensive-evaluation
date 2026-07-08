package edu.whut.eval.infra.persistence.query;

import java.math.BigDecimal;

public record ApprovedApplicationFactRow(
        Long applicationId,
        String categoryCode,
        String itemCode,
        BigDecimal scoreValue,
        String displayText,
        String sourceType,
        String sourceRefId
) {
}
