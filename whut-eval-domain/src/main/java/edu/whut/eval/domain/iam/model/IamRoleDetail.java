package edu.whut.eval.domain.iam.model;

public record IamRoleDetail(
        Long id,
        String roleCode,
        String roleName,
        String roleScope,
        String status
) {
}
