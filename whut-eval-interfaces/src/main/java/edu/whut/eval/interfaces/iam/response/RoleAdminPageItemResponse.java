package edu.whut.eval.interfaces.iam.response;

public record RoleAdminPageItemResponse(
        Long roleId,
        String roleCode,
        String roleName,
        String roleScope,
        String status,
        int permissionCount,
        String createdAt
) {
}
