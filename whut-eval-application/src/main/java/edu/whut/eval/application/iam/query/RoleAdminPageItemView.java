package edu.whut.eval.application.iam.query;

public record RoleAdminPageItemView(
        Long roleId,
        String roleCode,
        String roleName,
        String roleScope,
        String status,
        int permissionCount,
        String createdAt
) {
}
