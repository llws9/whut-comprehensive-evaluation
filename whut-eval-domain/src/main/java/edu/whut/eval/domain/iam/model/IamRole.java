package edu.whut.eval.domain.iam.model;

public record IamRole(
        Long roleId,
        String roleCode,
        String roleName,
        String roleScope,
        String status,
        String createdAt
) {
}
