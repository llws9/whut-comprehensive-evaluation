package edu.whut.eval.domain.iam.model;

/**
 * 角色定义快照。
 */
public record IamRoleDefinition(
        Long roleId,
        String roleCode,
        String roleName,
        String status
) {
}
