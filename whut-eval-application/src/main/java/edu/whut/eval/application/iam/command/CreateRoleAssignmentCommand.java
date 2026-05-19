package edu.whut.eval.application.iam.command;

/**
 * 创建角色分配命令。
 */
public record CreateRoleAssignmentCommand(
        Long userId,
        String roleCode,
        Long orgUnitId,
        String effectiveFrom,
        String effectiveTo,
        String sourceType
) {
}
