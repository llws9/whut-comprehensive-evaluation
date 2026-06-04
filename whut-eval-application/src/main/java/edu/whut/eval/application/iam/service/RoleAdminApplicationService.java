package edu.whut.eval.application.iam.service;

import edu.whut.eval.application.iam.command.CreateRoleCommand;
import edu.whut.eval.application.iam.command.ReplaceRolePermissionsCommand;
import edu.whut.eval.application.iam.command.UpdateRoleCommand;
import edu.whut.eval.application.iam.query.RoleCreatedView;

public interface RoleAdminApplicationService {

    RoleCreatedView createRole(CreateRoleCommand command);

    void updateRole(Long roleId, UpdateRoleCommand command);

    void replacePermissions(Long roleId, ReplaceRolePermissionsCommand command);
}
