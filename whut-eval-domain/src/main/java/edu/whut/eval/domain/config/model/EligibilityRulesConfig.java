package edu.whut.eval.domain.config.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EligibilityRulesConfig {

    @JsonProperty("eligibility-rules")
    private Map<String, List<EligibilityRuleItem>> eligibilityRules;

    public Map<String, List<EligibilityRuleItem>> getEligibilityRules() {
        return eligibilityRules;
    }

    public void setEligibilityRules(Map<String, List<EligibilityRuleItem>> eligibilityRules) {
        this.eligibilityRules = eligibilityRules;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EligibilityRuleItem {
        private String ruleId;
        private String ruleType;
        private String description;
        private String expression;
        private boolean enabled;

        public String getRuleId() {
            return ruleId;
        }

        public void setRuleId(String ruleId) {
            this.ruleId = ruleId;
        }

        public String getRuleType() {
            return ruleType;
        }

        public void setRuleType(String ruleType) {
            this.ruleType = ruleType;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getExpression() {
            return expression;
        }

        public void setExpression(String expression) {
            this.expression = expression;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
