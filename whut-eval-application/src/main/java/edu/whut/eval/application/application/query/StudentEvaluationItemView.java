package edu.whut.eval.application.application.query;

import java.math.BigDecimal;
import java.util.List;

public record StudentEvaluationItemView(
        String itemCode,
        String itemName,
        String categoryCode,
        String categoryName,
        String description,
        BigDecimal maxPoints,
        String applyMode,
        boolean enabled,
        List<StudentEvaluationOptionView> options
) {
}
