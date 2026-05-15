package edu.whut.eval.application.iam.query;

import edu.whut.eval.domain.iam.model.IamRoleAssignment;
import edu.whut.eval.domain.iam.model.IamUser;
import edu.whut.eval.domain.org.model.OrgMembership;

import java.util.List;

public record UserIdentityView(
        IamUser user,
        List<IamRoleAssignment> assignments,
        List<OrgMembership> memberships
) {
}
