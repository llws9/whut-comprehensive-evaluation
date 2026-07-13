package edu.whut.eval.application.finalrecord.importing;

import java.math.BigDecimal;

public record ActivityImportItemDefinition(
        String itemCode,
        String categoryCode,
        BigDecimal maxPoints,
        boolean allowOverflow
) {
}
