package edu.whut.eval.domain.iam.model;

/**
 * 角色分配管理快照。
 */
public record IamRoleAssignmentDetail(
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
