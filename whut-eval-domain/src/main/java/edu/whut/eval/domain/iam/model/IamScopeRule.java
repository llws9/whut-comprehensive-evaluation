package edu.whut.eval.domain.iam.model;

public record IamScopeRule(
        Long assignmentId,
        String permissionCode,
        String scopeType,
        Long orgUnitId,
        String categoryCode,
        String itemCode,
        String expressionJson,
        Integer priority,
        String status
) {
}
