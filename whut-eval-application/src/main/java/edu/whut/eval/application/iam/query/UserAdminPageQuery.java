package edu.whut.eval.application.iam.query;

public record UserAdminPageQuery(long pageNo,
                                 long pageSize,
                                 String keyword,
                                 String status,
                                 Long orgUnitId) {
}
