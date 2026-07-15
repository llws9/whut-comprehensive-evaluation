package edu.whut.eval.application.platform.query;

import java.math.BigDecimal;

public class EvaluationItemCommandResult extends EvaluationItemResponse {

    private final String status;

    public EvaluationItemCommandResult(String categoryCode,
                                       String categoryName,
                                       String itemCode,
                                       String itemName,
                                       String description,
                                       BigDecimal maxPoints,
                                       String maxPointsExpression,
                                       String applyMode,
                                       boolean enabled,
                                       String status,
                                       int sortOrder,
                                       String optionsKey) {
        super(categoryCode, categoryName, itemCode, itemName, description, maxPoints,
                maxPointsExpression, applyMode, enabled, sortOrder, optionsKey);
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}
