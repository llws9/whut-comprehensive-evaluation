package edu.whut.eval.application.iam.command;

public record ReplaceUserMembershipItemCommand(
        Long orgUnitId,
        boolean isPrimary
) {
}
