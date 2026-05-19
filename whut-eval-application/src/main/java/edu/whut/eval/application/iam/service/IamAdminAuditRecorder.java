package edu.whut.eval.application.iam.service;

import edu.whut.eval.domain.iam.model.IamRoleAssignmentDetail;

/**
 * IAM 管理端操作审计扩展点。
 * 当前先收敛 A-15 的角色分配更新审计，后续可以切换到数据库、MQ 或审计中心实现。
 */
public interface IamAdminAuditRecorder {

    void recordRoleAssignmentUpdated(Long operatorUserId,
                                     IamRoleAssignmentDetail before,
                                     IamRoleAssignmentDetail after);
}
