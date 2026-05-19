package edu.whut.eval.application.iam.service;

import edu.whut.eval.application.iam.command.CreateRoleAssignmentCommand;
import edu.whut.eval.application.iam.command.UpdateRoleAssignmentCommand;
import edu.whut.eval.application.iam.query.RoleAssignmentAdminView;

/**
 * 管理端角色分配应用服务契约。
 */
public interface RoleAssignmentAdminApplicationService {

    RoleAssignmentAdminView createAssignment(CreateRoleAssignmentCommand command);

    RoleAssignmentAdminView updateAssignment(Long assignmentId, UpdateRoleAssignmentCommand command);
}
