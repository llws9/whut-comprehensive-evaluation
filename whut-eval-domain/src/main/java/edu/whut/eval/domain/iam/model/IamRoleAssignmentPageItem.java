package edu.whut.eval.domain.iam.model;

/**
 * 管理端角色分配分页行领域投影。
 */
public record IamRoleAssignmentPageItem(Long assignmentId,
                                        Long userId,
                                        String userNo,
                                        String userName,
                                        String roleCode,
                                        String roleName,
                                        Long orgUnitId,
                                        String orgUnitName,
                                        String status,
                                        String effectiveFrom,
                                        String effectiveTo) {
}
