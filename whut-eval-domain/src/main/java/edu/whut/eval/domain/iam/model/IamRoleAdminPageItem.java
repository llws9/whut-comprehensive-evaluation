package edu.whut.eval.domain.iam.model;

public record IamRoleAdminPageItem(
        Long roleId,
        String roleCode,
        String roleName,
        String roleScope,
        String status,
        int permissionCount,
        String createdAt
) {
}
