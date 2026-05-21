package edu.whut.eval.application.iam.service;

import edu.whut.eval.application.iam.command.CreateUserCommand;
import edu.whut.eval.application.iam.command.ImportUsersCommand;
import edu.whut.eval.application.iam.command.UpdateUserStatusCommand;
import edu.whut.eval.application.iam.query.UserImportResultView;
import edu.whut.eval.application.iam.query.UserAdminView;

public interface UserAdminCommandApplicationService {

    UserAdminView createUser(CreateUserCommand command);

    UserImportResultView importUsers(ImportUsersCommand command);

    void updateUserStatus(Long userId, UpdateUserStatusCommand command);
}
