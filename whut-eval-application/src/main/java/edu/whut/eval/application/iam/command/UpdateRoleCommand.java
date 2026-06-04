package edu.whut.eval.application.iam.command;

public record UpdateRoleCommand(
        String roleName,
        String roleScope,
        String status,
        String expectedRoleName,
        String expectedRoleScope,
        String expectedStatus
) {
}
