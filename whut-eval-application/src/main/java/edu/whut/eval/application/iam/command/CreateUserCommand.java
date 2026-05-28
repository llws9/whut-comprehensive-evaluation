package edu.whut.eval.application.iam.command;

public record CreateUserCommand(
        String userNo,
        String userName,
        String passwordHash,
        String email,
        String phone,
        Long primaryOrgUnitId
) {
}