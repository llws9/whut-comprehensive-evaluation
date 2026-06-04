package edu.whut.eval.application.iam.query;

public record RoleCreatedView(
        Long roleId,
        String roleCode,
        String roleName,
        String roleScope,
        String status
) {
}
