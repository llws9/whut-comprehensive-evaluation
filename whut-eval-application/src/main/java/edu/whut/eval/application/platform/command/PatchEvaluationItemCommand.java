package edu.whut.eval.application.platform.command;

import java.math.BigDecimal;

public record PatchEvaluationItemCommand(
        String itemCode,
        String itemName,
        String description,
        BigDecimal maxPoints,
        String maxPointsExpression,
        String applyMode,
        Boolean enabled,
        String status,
        Integer sortOrder,
        String optionsKey
) {
}
