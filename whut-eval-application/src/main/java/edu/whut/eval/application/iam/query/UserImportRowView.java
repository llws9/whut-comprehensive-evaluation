package edu.whut.eval.application.iam.query;

public record UserImportRowView(
        long rowNo,
        String userNo,
        String userName,
        String password,
        String email,
        String phone
) {
}
