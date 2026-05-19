package edu.whut.eval.domain.iam.query;

/**
 * 管理端角色分配分页查询对象。
 */
public record RoleAssignmentPageQuery(long pageNo,
                                      long pageSize,
                                      Long userId,
                                      String roleCode,
                                      String status,
                                      Long orgUnitId) {
}
