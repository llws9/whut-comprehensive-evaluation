package edu.whut.eval.domain.config.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EvaluationItemsConfig {

    @JsonProperty("evaluation-items")
    private Map<String, List<EvaluationItem>> evaluationItems;

    public Map<String, List<EvaluationItem>> getEvaluationItems() {
        return evaluationItems;
    }

    public void setEvaluationItems(Map<String, List<EvaluationItem>> evaluationItems) {
        this.evaluationItems = evaluationItems;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EvaluationItem {
        private String itemCode;
        private String itemName;
        private String categoryCode;
        private String categoryName;
        private String description;
        private BigDecimal maxPoints;
        private String maxPointsExpression;
        private String scholarshipRequirement;
        private String applyMode;
        private boolean enabled;
        private int sortOrder;
        private String optionsKey;

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

        public String getCategoryCode() {
            return categoryCode;
        }

        public void setCategoryCode(String categoryCode) {
            this.categoryCode = categoryCode;
        }

        public String getCategoryName() {
            return categoryName;
        }

        public void setCategoryName(String categoryName) {
            this.categoryName = categoryName;
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

        public String getScholarshipRequirement() {
            return scholarshipRequirement;
        }

        public void setScholarshipRequirement(String scholarshipRequirement) {
            this.scholarshipRequirement = scholarshipRequirement;
        }

        public String getApplyMode() {
            return applyMode;
        }

        public void setApplyMode(String applyMode) {
            this.applyMode = applyMode;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getSortOrder() {
            return sortOrder;
        }

        public void setSortOrder(int sortOrder) {
            this.sortOrder = sortOrder;
        }

        public String getOptionsKey() {
            return optionsKey;
        }

        public void setOptionsKey(String optionsKey) {
            this.optionsKey = optionsKey;
        }
    }
}
