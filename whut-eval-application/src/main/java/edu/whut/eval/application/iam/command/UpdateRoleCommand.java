package edu.whut.eval.application.iam.command;

public record UpdateRoleCommand(
        Long roleId,
        String roleName,
        String status
) {
}
