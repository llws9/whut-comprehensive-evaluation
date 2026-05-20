package edu.whut.eval.application.iam.service;

import edu.whut.eval.application.iam.command.ReplaceUserMembershipsCommand;
import edu.whut.eval.application.iam.query.UserMembershipAdminView;

import java.util.List;

public interface UserMembershipAdminApplicationService {

    List<UserMembershipAdminView> listMemberships(Long userId);

    void replaceMemberships(Long userId, ReplaceUserMembershipsCommand command);
}
