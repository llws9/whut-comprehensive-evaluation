package edu.whut.eval.application.iam.service;

import edu.whut.eval.application.iam.command.CreateRoleAssignmentCommand;
import edu.whut.eval.application.iam.command.UpdateRoleAssignmentCommand;
import edu.whut.eval.application.iam.query.RoleAssignmentAdminPageItemView;
import edu.whut.eval.application.iam.query.RoleAssignmentAdminPageQuery;
import edu.whut.eval.application.iam.query.RoleAssignmentAdminView;
import edu.whut.eval.domain.shared.PageResult;
/**
 * 管理端角色分配应用服务草稿实现。
 * 仅保留为历史草稿参考，不再注册为 Spring Bean。
 */
public class DraftRoleAssignmentAdminApplicationService implements RoleAssignmentAdminApplicationService {

    @Override
    public RoleAssignmentAdminView createAssignment(CreateRoleAssignmentCommand command) {
        throw new UnsupportedOperationException("TODO: implement create role assignment flow");
    }

    @Override
    public RoleAssignmentAdminView updateAssignment(Long assignmentId, UpdateRoleAssignmentCommand command) {
        throw new UnsupportedOperationException("TODO: implement update role assignment flow");
    }

    @Override
    public PageResult<RoleAssignmentAdminPageItemView> pageAssignments(RoleAssignmentAdminPageQuery query) {
        throw new UnsupportedOperationException("TODO: implement page role assignment flow");
    }

    @Override
    public void revokeAssignment(Long assignmentId) {
        throw new UnsupportedOperationException("TODO: implement revoke role assignment flow");
    }
}
