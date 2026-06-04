package edu.whut.eval.application.iam.query;

public record RoleAdminView(
        Long roleId,
        String roleCode,
        String roleName,
        String roleScope,
        String status,
        int permissionCount,
        String createdAt
) {
}
