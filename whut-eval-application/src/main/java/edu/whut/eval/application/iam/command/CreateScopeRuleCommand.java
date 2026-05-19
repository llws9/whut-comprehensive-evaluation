package edu.whut.eval.application.iam.command;

import java.util.Map;

/**
 * 新增范围规则命令。
 */
public record CreateScopeRuleCommand(
        String permissionCode,
        String scopeType,
        Long orgUnitId,
        String categoryCode,
        String itemCode,
        Map<String, Object> expressionJson,
        Integer priority
) {
}
