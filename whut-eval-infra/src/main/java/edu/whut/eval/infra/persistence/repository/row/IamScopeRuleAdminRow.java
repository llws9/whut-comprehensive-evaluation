package edu.whut.eval.infra.persistence.repository.row;

import java.time.LocalDateTime;

public class IamScopeRuleAdminRow {

    private Long scopeRuleId;
    private Long assignmentId;
    private String permissionCode;
    private String scopeType;
    private Long orgUnitId;
    private String orgUnitName;
    private String categoryCode;
    private String itemCode;
    private String expressionJson;
    private Integer priority;
    private String status;
    private LocalDateTime createdAt;

    public Long getScopeRuleId() {
        return scopeRuleId;
    }

    public void setScopeRuleId(Long scopeRuleId) {
        this.scopeRuleId = scopeRuleId;
    }

    public Long getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(Long assignmentId) {
        this.assignmentId = assignmentId;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public void setPermissionCode(String permissionCode) {
        this.permissionCode = permissionCode;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public Long getOrgUnitId() {
        return orgUnitId;
    }

    public void setOrgUnitId(Long orgUnitId) {
        this.orgUnitId = orgUnitId;
    }

    public String getOrgUnitName() {
        return orgUnitName;
    }

    public void setOrgUnitName(String orgUnitName) {
        this.orgUnitName = orgUnitName;
    }

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

    public String getExpressionJson() {
        return expressionJson;
    }

    public void setExpressionJson(String expressionJson) {
        this.expressionJson = expressionJson;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
