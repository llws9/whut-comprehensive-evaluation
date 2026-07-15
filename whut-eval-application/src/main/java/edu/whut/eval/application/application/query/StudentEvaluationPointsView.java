package edu.whut.eval.application.application.query;

import java.math.BigDecimal;

public record StudentEvaluationPointsView(
        String itemCode,
        String optionCode,
        BigDecimal points,
        String optionName
) {
}
