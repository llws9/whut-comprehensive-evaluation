package edu.whut.eval.application.auth.model;

import java.util.Locale;
import java.util.Objects;

public class AuthorizationScope {

    private final String permissionCode;
    private final String scopeType;
    private final Long orgUnitId;
    private final String categoryCode;
    private final String itemCode;
    private final String expressionJson;
    private final Integer priority;

    public AuthorizationScope(String permissionCode,
                              String scopeType,
                              Long orgUnitId,
                              String categoryCode,
                              String itemCode,
                              String expressionJson,
                              Integer priority) {
        this.permissionCode = permissionCode;
        this.scopeType = scopeType;
        this.orgUnitId = orgUnitId;
        this.categoryCode = categoryCode;
        this.itemCode = itemCode;
        this.expressionJson = expressionJson;
        this.priority = priority;
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

    public String getCategoryCode() {
        return categoryCode;
    }

    public String getItemCode() {
        return itemCode;
    }

    public String getExpressionJson() {
        return expressionJson;
    }

    public Integer getPriority() {
        return priority;
    }

    public boolean isScopeType(String expectedScopeType) {
        if (expectedScopeType == null || expectedScopeType.isBlank()) {
            return false;
        }
        return normalize(scopeType).equals(normalize(expectedScopeType));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AuthorizationScope)) {
            return false;
        }
        AuthorizationScope that = (AuthorizationScope) other;
        return Objects.equals(permissionCode, that.permissionCode)
                && Objects.equals(scopeType, that.scopeType)
                && Objects.equals(orgUnitId, that.orgUnitId)
                && Objects.equals(categoryCode, that.categoryCode)
                && Objects.equals(itemCode, that.itemCode)
                && Objects.equals(expressionJson, that.expressionJson)
                && Objects.equals(priority, that.priority);
    }

    @Override
    public int hashCode() {
        return Objects.hash(permissionCode, scopeType, orgUnitId, categoryCode, itemCode, expressionJson, priority);
    }
}
