package edu.whut.eval.domain.iam.query;

/**
 * 管理端用户分页查询对象。
 */
public record UserAdminPageQuery(long pageNo,
                                 long pageSize,
                                 String keyword,
                                 String status,
                                 Long orgUnitId) {
}
