package edu.whut.eval.domain.config.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class IndexOptionsConfig {

    @JsonProperty("index-options")
    private Map<String, List<OptionItem>> indexOptions;

    public Map<String, List<OptionItem>> getIndexOptions() {
        return indexOptions;
    }

    public void setIndexOptions(Map<String, List<OptionItem>> indexOptions) {
        this.indexOptions = indexOptions;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OptionItem {
        private String optionCode;
        private String optionName;
        private BigDecimal points;
        private String description;
        private String condition;
        private int sortOrder;
        private boolean allowCustomPoints;

        public String getOptionCode() {
            return optionCode;
        }

        public void setOptionCode(String optionCode) {
            this.optionCode = optionCode;
        }

        public String getOptionName() {
            return optionName;
        }

        public void setOptionName(String optionName) {
            this.optionName = optionName;
        }

        public BigDecimal getPoints() {
            return points;
        }

        public void setPoints(BigDecimal points) {
            this.points = points;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getCondition() {
            return condition;
        }

        public void setCondition(String condition) {
            this.condition = condition;
        }

        public int getSortOrder() {
            return sortOrder;
        }

        public void setSortOrder(int sortOrder) {
            this.sortOrder = sortOrder;
        }

        public boolean isAllowCustomPoints() {
            return allowCustomPoints;
        }

        public void setAllowCustomPoints(boolean allowCustomPoints) {
            this.allowCustomPoints = allowCustomPoints;
        }
    }
}
