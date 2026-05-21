package edu.whut.eval.domain.iam.model;

import java.util.List;

/**
 * 管理端用户分页行领域投影。
 */
public record IamUserAdminPageItem(Long userId,
                                   String userNo,
                                   String userName,
                                   String status,
                                   List<String> orgUnits,
                                   List<String> roleCodes,
                                   String createdAt) {
}
