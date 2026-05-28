package edu.whut.eval.domain.iam.query;

public record RoleAdminPageQuery(
        long pageNo,
        long pageSize,
        String keyword,
        String status
) {
}
