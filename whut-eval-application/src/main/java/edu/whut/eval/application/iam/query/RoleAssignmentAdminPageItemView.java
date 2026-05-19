package edu.whut.eval.application.iam.query;

/**
 * 管理端角色分配分页行视图。
 */
public record RoleAssignmentAdminPageItemView(
        Long assignmentId,
        Long userId,
        String userNo,
        String userName,
        String roleCode,
        String roleName,
        Long orgUnitId,
        String orgUnitName,
        String status,
        String effectiveFrom,
        String effectiveTo
) {
}
