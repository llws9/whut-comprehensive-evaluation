package edu.whut.eval.interfaces.platform.request;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public class CreateEvaluationItemRequest {

    @NotBlank(message = "categoryCode 不能为空")
    private String categoryCode;

    @NotBlank(message = "itemCode 不能为空")
    private String itemCode;

    @NotBlank(message = "itemName 不能为空")
    private String itemName;

    private String description;
    private BigDecimal maxPoints;
    private String maxPointsExpression;

    @NotBlank(message = "applyMode 不能为空")
    private String applyMode;

    private Boolean enabled;
    private String status;
    private Integer sortOrder;
    private String optionsKey;

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getMaxPoints() {
        return maxPoints;
    }

    public void setMaxPoints(BigDecimal maxPoints) {
        this.maxPoints = maxPoints;
    }

    public String getMaxPointsExpression() {
        return maxPointsExpression;
    }

    public void setMaxPointsExpression(String maxPointsExpression) {
        this.maxPointsExpression = maxPointsExpression;
    }

    public String getApplyMode() {
        return applyMode;
    }

    public void setApplyMode(String applyMode) {
        this.applyMode = applyMode;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getOptionsKey() {
        return optionsKey;
    }

    public void setOptionsKey(String optionsKey) {
        this.optionsKey = optionsKey;
    }
}
