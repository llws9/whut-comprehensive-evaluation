package edu.whut.eval.interfaces.iam.response;

import java.util.Map;

/**
 * 范围规则响应。
 */
public class ScopeRuleResponse {

    private final Long scopeRuleId;
    private final Long assignmentId;
    private final String permissionCode;
    private final String scopeType;
    private final Long orgUnitId;
    private final String orgUnitName;
    private final String categoryCode;
    private final String itemCode;
    private final Map<String, Object> expressionJson;
    private final Integer priority;
    private final String status;
    private final String createdAt;

    public ScopeRuleResponse(Long scopeRuleId,
                             Long assignmentId,
                             String permissionCode,
                             String scopeType,
                             Long orgUnitId,
                             String orgUnitName,
                             String categoryCode,
                             String itemCode,
                             Map<String, Object> expressionJson,
                             Integer priority,
                             String status,
                             String createdAt) {
        this.scopeRuleId = scopeRuleId;
        this.assignmentId = assignmentId;
        this.permissionCode = permissionCode;
        this.scopeType = scopeType;
        this.orgUnitId = orgUnitId;
        this.orgUnitName = orgUnitName;
        this.categoryCode = categoryCode;
        this.itemCode = itemCode;
        this.expressionJson = expressionJson;
        this.priority = priority;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getScopeRuleId() {
        return scopeRuleId;
    }

    public Long getAssignmentId() {
        return assignmentId;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public String getScopeType() {
        return scopeType;
    }

    public Long getOrgUnitId() {
        return orgUnitId;
    }

    public String getOrgUnitName() {
        return orgUnitName;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public String getItemCode() {
        return itemCode;
    }

    public Map<String, Object> getExpressionJson() {
        return expressionJson;
    }

    public Integer getPriority() {
        return priority;
    }

    public String getStatus() {
        return status;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
