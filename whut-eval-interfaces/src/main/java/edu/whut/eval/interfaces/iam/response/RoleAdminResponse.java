package edu.whut.eval.interfaces.iam.response;

public record RoleAdminResponse(
        Long roleId,
        String roleCode,
        String roleName,
        String roleScope,
        String status
) {
}
