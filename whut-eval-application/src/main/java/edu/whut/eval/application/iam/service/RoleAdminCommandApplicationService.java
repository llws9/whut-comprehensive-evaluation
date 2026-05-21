package edu.whut.eval.application.iam.service;

import edu.whut.eval.application.iam.command.CreateRoleCommand;
import edu.whut.eval.application.iam.command.ReplaceRolePermissionsCommand;
import edu.whut.eval.application.iam.command.UpdateRoleCommand;
import edu.whut.eval.application.iam.query.RoleAdminView;

public interface RoleAdminCommandApplicationService {

    RoleAdminView createRole(CreateRoleCommand command);

    void updateRole(Long roleId, UpdateRoleCommand command);

    void replaceRolePermissions(Long roleId, ReplaceRolePermissionsCommand command);
}
