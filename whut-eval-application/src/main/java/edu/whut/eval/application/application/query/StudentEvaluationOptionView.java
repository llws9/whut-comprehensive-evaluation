package edu.whut.eval.application.application.query;

import java.math.BigDecimal;

public record StudentEvaluationOptionView(
        String optionCode,
        String optionName,
        BigDecimal points,
        String description
) {
}
