package edu.whut.eval.application.iam.query;

/**
 * 管理端角色分配分页查询条件。
 */
public record RoleAssignmentAdminPageQuery(
        long pageNo,
        long pageSize,
        Long userId,
        String roleCode,
        String status,
        Long orgUnitId
) {
}
