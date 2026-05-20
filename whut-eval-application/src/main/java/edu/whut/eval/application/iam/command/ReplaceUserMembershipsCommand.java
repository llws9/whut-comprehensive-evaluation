package edu.whut.eval.application.iam.command;

import java.util.List;

public record ReplaceUserMembershipsCommand(
        List<ReplaceUserMembershipItemCommand> memberships
) {
}
