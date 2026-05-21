package edu.whut.eval.application.iam.query;

public record RoleAdminPageQuery(
        long pageNo,
        long pageSize,
        String keyword,
        String status
) {
}
