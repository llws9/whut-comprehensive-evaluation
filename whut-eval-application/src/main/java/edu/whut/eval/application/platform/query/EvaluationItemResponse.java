package edu.whut.eval.application.platform.query;

import java.math.BigDecimal;

public class EvaluationItemResponse {

    private final String categoryCode;
    private final String categoryName;
    private final String itemCode;
    private final String itemName;
    private final String description;
    private final BigDecimal maxPoints;
    private final String maxPointsExpression;
    private final String applyMode;
    private final boolean enabled;
    private final int sortOrder;
    private final String optionsKey;

    public EvaluationItemResponse(String categoryCode, String categoryName, String itemCode, String itemName,
                                  String description, BigDecimal maxPoints, String maxPointsExpression,
                                  String applyMode, boolean enabled, int sortOrder, String optionsKey) {
        this.categoryCode = categoryCode;
        this.categoryName = categoryName;
        this.itemCode = itemCode;
        this.itemName = itemName;
        this.description = description;
        this.maxPoints = maxPoints;
        this.maxPointsExpression = maxPointsExpression;
        this.applyMode = applyMode;
        this.enabled = enabled;
        this.sortOrder = sortOrder;
        this.optionsKey = optionsKey;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getItemCode() {
        return itemCode;
    }

    public String getItemName() {
        return itemName;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getMaxPoints() {
        return maxPoints;
    }

    public String getMaxPointsExpression() {
        return maxPointsExpression;
    }

    public String getApplyMode() {
        return applyMode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getOptionsKey() {
        return optionsKey;
    }
}
