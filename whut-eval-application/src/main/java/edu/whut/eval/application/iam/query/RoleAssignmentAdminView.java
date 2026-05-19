package edu.whut.eval.application.iam.query;

/**
 * 管理端角色分配视图。
 */
public record RoleAssignmentAdminView(
        Long assignmentId,
        Long userId,
        String roleCode,
        String roleName,
        Long orgUnitId,
        String orgUnitName,
        String status,
        String effectiveFrom,
        String effectiveTo,
        String sourceType,
        String updatedAt
) {
}
