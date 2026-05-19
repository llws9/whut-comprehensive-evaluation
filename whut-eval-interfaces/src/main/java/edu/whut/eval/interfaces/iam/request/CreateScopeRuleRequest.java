package edu.whut.eval.interfaces.iam.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.Map;

/**
 * 新增范围规则请求。
 */
public class CreateScopeRuleRequest {

    @NotBlank
    private String permissionCode;

    @NotBlank
    private String scopeType;

    @Positive
    private Long orgUnitId;

    private String categoryCode;

    private String itemCode;

    private Map<String, Object> expressionJson;

    private Integer priority;

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

    public Map<String, Object> getExpressionJson() {
        return expressionJson;
    }

    public void setExpressionJson(Map<String, Object> expressionJson) {
        this.expressionJson = expressionJson;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }
}
