package edu.whut.eval.application.iam.command;

/**
 * 修改角色分配命令。
 */
public record UpdateRoleAssignmentCommand(
        String status,
        Long orgUnitId,
        String effectiveFrom,
        String effectiveTo
) {
}
