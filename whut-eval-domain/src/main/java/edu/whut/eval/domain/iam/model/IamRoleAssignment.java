package edu.whut.eval.domain.iam.model;

public record IamRoleAssignment(
        Long assignmentId,
        Long roleId,
        String roleCode,
        String roleName,
        Long orgUnitId,
        String status
) {
}
