package edu.whut.eval.domain.iam.model;

import java.util.Map;

/**
 * 范围规则管理快照。
 */
public record IamScopeRuleDetail(
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
