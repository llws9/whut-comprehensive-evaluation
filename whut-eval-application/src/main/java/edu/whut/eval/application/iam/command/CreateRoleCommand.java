package edu.whut.eval.application.iam.command;

public record CreateRoleCommand(
        String roleCode,
        String roleName
) {
}
