package edu.whut.eval.application.iam.query;

import java.util.Map;

/**
 * 管理端范围规则视图。
 */
public record ScopeRuleAdminView(
        Long scopeRuleId,
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
        String createdAt
) {
}
